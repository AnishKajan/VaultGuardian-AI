package com.vaultguardian.service;

import com.vaultguardian.dto.DashboardAnalyticsDto;
import com.vaultguardian.dto.RiskDistribution;
import com.vaultguardian.dto.CategoryDistribution;
import com.vaultguardian.dto.RecentActivity;
import com.vaultguardian.entity.Document;
import com.vaultguardian.entity.User;
import com.vaultguardian.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final DocumentProcessingService documentProcessingService;
    private final AuditService auditService;
    
    @Transactional
    public Document uploadDocument(MultipartFile file, User user) {
        log.info("🚀 Starting document upload for user: {}, file: {}", user.getUsername(), file.getOriginalFilename());
        
        try {
            // Calculate SHA256 hash
            String sha256Hash = calculateSHA256(file.getBytes());
            log.info("🔐 SHA256 hash calculated: {}", sha256Hash);
            
            // Check for duplicate
            if (documentRepository.existsBySha256Hash(sha256Hash)) {
                log.warn("❌ Duplicate document detected: {}", sha256Hash);
                throw new IllegalArgumentException("Document already exists");
            }
            
            // Upload to storage (Azure Blob Storage)
            String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());
            log.info("📁 Generated unique filename: {}", uniqueFilename);
            
            // Use StorageService (Azure Blob Storage)
            String storageKey = storageService.uploadFile(file.getBytes(), uniqueFilename, file.getContentType());
            String containerName = getContainerName();
            
            log.info("☁️ File uploaded to Azure Blob Storage successfully. Container: {}, Key: {}", containerName, storageKey);
            
            // Create document entity
            Document document = Document.builder()
                    .originalFilename(file.getOriginalFilename())
                    .filename(uniqueFilename)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .sha256Hash(sha256Hash)
                    .s3Key(storageKey) // Using s3Key field for Azure blob name (backward compatibility)
                    .s3Bucket(containerName) // Using s3Bucket field for Azure container name
                    .status(Document.DocumentStatus.SCANNING)
                    .riskLevel(Document.RiskLevel.MEDIUM)
                    .uploadedBy(user)
                    .isQuarantined(false)
                    .detectedFlags(new ArrayList<>())
                    .categories(new ArrayList<>())
                    .riskSummary("File uploaded - security scanning in progress...")
                    .build();
            
            // Save to database
            document = documentRepository.save(document);
            log.info("💾 Document saved to database with ID: {}", document.getId());
            
            // Start async processing
            log.info("🚀 Starting async processing for document ID: {}", document.getId());
            documentProcessingService.processDocumentAsync(document.getId());
            log.info("✅ Async processing initiated successfully");
            
            auditService.logDocumentUpload(user, document);
            
            log.info("✅ Upload completed successfully - Document ID: {} (Processing started)", document.getId());
            return document;
            
        } catch (Exception e) {
            log.error("❌ Error uploading document", e);
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }
    
    private String getContainerName() {
        // Handle Azure Blob Storage service
        try {
            if (storageService instanceof AzureBlobService) {
                return ((AzureBlobService) storageService).getContainerName();
            }
        } catch (Exception e) {
            log.warn("Error getting container name from storage service, using default", e);
        }
        return "vaultguardian-ai"; // default container name
    }
    
    private String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private String generateUniqueFilename(String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot != -1) {
            extension = originalFilename.substring(lastDot);
        }
        return timestamp + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    @Transactional(readOnly = true)
    public List<Document> getUserDocuments(User user) {
        if (user == null) {
            log.error("User is null in getUserDocuments");
            return new ArrayList<>();
        }
        
        try {
            List<Document> documents = documentRepository.findByUploadedByOrderByCreatedAtDesc(user);
            return documents != null ? documents : new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching documents for user: {}", user.getUsername(), e);
            return new ArrayList<>();
        }
    }
    
    @Transactional
    public void updateLastAccessed(Long documentId) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
            document.setLastAccessedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Error updating last accessed for document: {}", documentId, e);
        }
    }
    
    public byte[] downloadDocument(Long documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        
        // Check permissions
        if (!canUserAccessDocument(user, document)) {
            throw new SecurityException("Access denied");
        }
        
        updateLastAccessed(documentId);
        auditService.logDocumentAccess(user, document);
        
        // Use StorageService (Azure Blob Storage)
        return storageService.downloadFile(document.getS3Key());
    }
    
    private boolean canUserAccessDocument(User user, Document document) {
        try {
            // Basic access control - owner can access, admins can access all
            if (user == null || document == null || document.getUploadedBy() == null) {
                return false;
            }
            
            return document.getUploadedBy().getId().equals(user.getId()) ||
                   (user.getRoles() != null && (
                       user.getRoles().contains(User.Role.ADMIN) ||
                       user.getRoles().contains(User.Role.SECURITY_OFFICER)
                   ));
        } catch (Exception e) {
            log.error("Error checking user access for document: {}", document != null ? document.getId() : "unknown", e);
            return false;
        }
    }
    
    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        
        // Check permissions
        if (!canUserAccessDocument(user, document)) {
            throw new SecurityException("Access denied");
        }
        
        return document;
    }
    
    @Transactional
    public void deleteDocument(Long documentId, User user) {
        Document document = getDocumentById(documentId, user);
        
        // Check if user can delete (only owner or admin)
        if (!document.getUploadedBy().getId().equals(user.getId()) && 
            (user.getRoles() == null || !user.getRoles().contains(User.Role.ADMIN))) {
            throw new SecurityException("Access denied");
        }
        
        // Delete from storage (Azure Blob Storage)
        try {
            storageService.deleteFile(document.getS3Key());
        } catch (Exception e) {
            log.warn("Failed to delete file from storage: {}", e.getMessage());
        }
        
        // Delete from database
        documentRepository.delete(document);
        
        auditService.logDocumentDeletion(user, document);
        log.info("Document deleted - ID: {}, User: {}", documentId, user.getUsername());
    }
    
    @Transactional(readOnly = true)
    public List<Document> searchDocuments(String query, User user, Pageable pageable) {
        try {
            // Simple search implementation - you can enhance this
            return documentRepository.searchByUser(user, query, pageable);
        } catch (Exception e) {
            log.error("Error searching documents for user: {}", user.getUsername(), e);
            return new ArrayList<>();
        }
    }
    
    @Transactional(readOnly = true)
    public DashboardAnalyticsDto getDashboardAnalytics(User user) {
        if (user == null) {
            log.error("User is null in getDashboardAnalytics");
            return createEmptyAnalytics();
        }
        
        try {
            // Get user documents
            List<Document> userDocuments = documentRepository.findByUploadedByOrderByCreatedAtDesc(user);
            
            if (userDocuments == null) {
                userDocuments = new ArrayList<>();
            }
            
            long totalDocuments = userDocuments.size();
            long quarantinedDocuments = userDocuments.stream()
                    .filter(doc -> doc.getIsQuarantined() != null && doc.getIsQuarantined())
                    .count();
            
            long highRiskDocuments = userDocuments.stream()
                    .filter(doc -> doc.getRiskLevel() != null && 
                                  (doc.getRiskLevel() == Document.RiskLevel.HIGH || 
                                   doc.getRiskLevel() == Document.RiskLevel.CRITICAL))
                    .count();
            
            // Get documents uploaded today
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long documentsToday = userDocuments.stream()
                    .filter(doc -> doc.getCreatedAt() != null && doc.getCreatedAt().isAfter(startOfDay))
                    .count();
            
            // Calculate risk distribution
            List<RiskDistribution> riskDistribution = calculateRiskDistribution(userDocuments);
            
            // Calculate category distribution
            List<CategoryDistribution> categoryDistribution = calculateCategoryDistribution(userDocuments);
            
            // Get recent activity
            List<RecentActivity> recentActivity = getRecentActivity(userDocuments);
            
            return DashboardAnalyticsDto.builder()
                    .totalDocuments(totalDocuments)
                    .documentsToday(documentsToday)
                    .quarantinedDocuments(quarantinedDocuments)
                    .highRiskDocuments(highRiskDocuments)
                    .riskDistribution(riskDistribution != null ? riskDistribution : new ArrayList<>())
                    .categoryDistribution(categoryDistribution != null ? categoryDistribution : new ArrayList<>())
                    .recentActivity(recentActivity != null ? recentActivity : new ArrayList<>())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error calculating analytics for user: {}", user != null ? user.getUsername() : "null", e);
            return createEmptyAnalytics();
        }
    }
    
    private DashboardAnalyticsDto createEmptyAnalytics() {
        return DashboardAnalyticsDto.builder()
                .totalDocuments(0L)
                .documentsToday(0L)
                .quarantinedDocuments(0L)
                .highRiskDocuments(0L)
                .riskDistribution(new ArrayList<>())
                .categoryDistribution(new ArrayList<>())
                .recentActivity(new ArrayList<>())
                .build();
    }
    
    @Transactional
    public void quarantineDocument(Long documentId, String reason, User user) {
        try {
            Document document = getDocumentById(documentId, user);
            document.setStatus(Document.DocumentStatus.QUARANTINED);
            document.setIsQuarantined(true);
            document.setQuarantineReason(reason);
            document.setRiskLevel(Document.RiskLevel.CRITICAL);
            documentRepository.save(document);
            
            auditService.logDocumentQuarantine(document, reason);
            log.warn("Document quarantined - ID: {}, Reason: {}", document.getId(), reason);
        } catch (Exception e) {
            log.error("Error quarantining document: {}", documentId, e);
            throw new RuntimeException("Failed to quarantine document", e);
        }
    }
    
    // Helper methods for analytics
    private List<RiskDistribution> calculateRiskDistribution(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Map<Document.RiskLevel, Long> riskCounts = documents.stream()
                    .filter(doc -> doc.getRiskLevel() != null)
                    .collect(Collectors.groupingBy(Document::getRiskLevel, Collectors.counting()));
            
            long total = documents.size();
            
            return riskCounts.entrySet().stream()
                    .map(entry -> RiskDistribution.builder()
                            .riskLevel(entry.getKey())
                            .count(entry.getValue())
                            .percentage(total > 0 ? (entry.getValue() * 100.0 / total) : 0.0)
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error calculating risk distribution", e);
            return new ArrayList<>();
        }
    }
    
    private List<CategoryDistribution> calculateCategoryDistribution(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Map<String, Long> categoryCounts = documents.stream()
                    .filter(doc -> doc.getCategories() != null && !doc.getCategories().isEmpty())
                    .flatMap(doc -> doc.getCategories().stream())
                    .filter(category -> category != null && !category.trim().isEmpty())
                    .collect(Collectors.groupingBy(category -> category, Collectors.counting()));
            
            if (categoryCounts.isEmpty()) {
                return new ArrayList<>();
            }
            
            long total = categoryCounts.values().stream().mapToLong(Long::longValue).sum();
            
            return categoryCounts.entrySet().stream()
                    .map(entry -> CategoryDistribution.builder()
                            .category(entry.getKey())
                            .count(entry.getValue())
                            .percentage(total > 0 ? (entry.getValue() * 100.0 / total) : 0.0)
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error calculating category distribution", e);
            return new ArrayList<>();
        }
    }
    
    private List<RecentActivity> getRecentActivity(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return documents.stream()
                    .filter(doc -> doc.getCreatedAt() != null && 
                                  doc.getOriginalFilename() != null && 
                                  doc.getUploadedBy() != null &&
                                  doc.getUploadedBy().getUsername() != null &&
                                  doc.getRiskLevel() != null)
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(10)
                    .map(doc -> RecentActivity.builder()
                            .action("Uploaded")
                            .documentName(doc.getOriginalFilename())
                            .username(doc.getUploadedBy().getUsername())
                            .timestamp(doc.getCreatedAt())
                            .riskLevel(doc.getRiskLevel())
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting recent activity", e);
            return new ArrayList<>();
        }
    }
}