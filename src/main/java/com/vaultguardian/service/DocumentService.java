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
    private final StorageService storageService; // CHANGED from S3Service
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
            
            // Upload to storage (S3 or Supabase based on configuration)
            String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());
            log.info("📁 Generated unique filename: {}", uniqueFilename);
            
            // Use StorageService instead of S3Service
            String storageKey = storageService.uploadFile(file.getBytes(), uniqueFilename, file.getContentType());
            String bucketName = getBucketName();
            
            log.info("☁️ File uploaded to storage successfully. Bucket: {}, Key: {}", bucketName, storageKey);
            
            // Create document entity
            Document document = Document.builder()
                    .originalFilename(file.getOriginalFilename())
                    .filename(uniqueFilename)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .sha256Hash(sha256Hash)
                    .s3Key(storageKey) // This works for both S3 and Supabase
                    .s3Bucket(bucketName)
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
    
    private String getBucketName() {
        // Handle both S3 and Supabase services
        if (storageService instanceof S3Service) {
            return ((S3Service) storageService).getBucketName();
        } else if (storageService instanceof SupabaseStorageService) {
            return ((SupabaseStorageService) storageService).getBucketName();
        }
        return "documents"; // default
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
        return documentRepository.findByUploadedByOrderByCreatedAtDesc(user);
    }
    
    @Transactional
    public void updateLastAccessed(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setLastAccessedAt(LocalDateTime.now());
        documentRepository.save(document);
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
        
        // Use StorageService instead of S3Service
        return storageService.downloadFile(document.getS3Key());
    }
    
    private boolean canUserAccessDocument(User user, Document document) {
        // Basic access control - owner can access, admins can access all
        return document.getUploadedBy().getId().equals(user.getId()) ||
               user.getRoles().contains(User.Role.ADMIN) ||
               user.getRoles().contains(User.Role.SECURITY_OFFICER);
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
            !user.getRoles().contains(User.Role.ADMIN)) {
            throw new SecurityException("Access denied");
        }
        
        // Delete from storage
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
        // Simple search implementation - you can enhance this
        return documentRepository.searchByUser(user, query, pageable);
    }
    
    @Transactional(readOnly = true)
    public DashboardAnalyticsDto getDashboardAnalytics(User user) {
        // Get user documents
        List<Document> userDocuments = documentRepository.findByUploadedByOrderByCreatedAtDesc(user);
        
        long totalDocuments = userDocuments.size();
        long quarantinedDocuments = userDocuments.stream()
                .filter(doc -> doc.getIsQuarantined() != null && doc.getIsQuarantined())
                .count();
        
        long highRiskDocuments = userDocuments.stream()
                .filter(doc -> doc.getRiskLevel() == Document.RiskLevel.HIGH || 
                              doc.getRiskLevel() == Document.RiskLevel.CRITICAL)
                .count();
        
        // Get documents uploaded today
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long documentsToday = userDocuments.stream()
                .filter(doc -> doc.getCreatedAt().isAfter(startOfDay))
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
                .riskDistribution(riskDistribution)
                .categoryDistribution(categoryDistribution)
                .recentActivity(recentActivity)
                .build();
    }
    
    @Transactional
    public void quarantineDocument(Long documentId, String reason, User user) {
        Document document = getDocumentById(documentId, user);
        document.setStatus(Document.DocumentStatus.QUARANTINED);
        document.setIsQuarantined(true);
        document.setQuarantineReason(reason);
        document.setRiskLevel(Document.RiskLevel.CRITICAL);
        documentRepository.save(document);
        
        auditService.logDocumentQuarantine(document, reason);
        log.warn("Document quarantined - ID: {}, Reason: {}", document.getId(), reason);
    }
    
    // Helper methods for analytics
    private List<RiskDistribution> calculateRiskDistribution(List<Document> documents) {
        if (documents.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<Document.RiskLevel, Long> riskCounts = documents.stream()
                .collect(Collectors.groupingBy(Document::getRiskLevel, Collectors.counting()));
        
        long total = documents.size();
        
        return riskCounts.entrySet().stream()
                .map(entry -> RiskDistribution.builder()
                        .riskLevel(entry.getKey())
                        .count(entry.getValue())
                        .percentage(total > 0 ? (entry.getValue() * 100.0 / total) : 0.0)
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<CategoryDistribution> calculateCategoryDistribution(List<Document> documents) {
        Map<String, Long> categoryCounts = documents.stream()
                .filter(doc -> doc.getCategories() != null)
                .flatMap(doc -> doc.getCategories().stream())
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
    }
    
    private List<RecentActivity> getRecentActivity(List<Document> documents) {
        return documents.stream()
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
    }
}