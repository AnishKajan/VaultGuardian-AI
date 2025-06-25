package com.vaultguardian.service;

import com.vaultguardian.entity.Document;
import com.vaultguardian.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DocumentProcessingService {
    
    private final DocumentRepository documentRepository;
    private final S3Service s3Service; // Keep as S3Service for now - simpler approach
    private final HuggingFaceService huggingFaceService;
    private final MalwareScanService malwareScanService;
    private final PolicyEnforcementService policyEnforcementService;
    private final AuditService auditService;
    private final Tika tika = new Tika();
    
    // FIXED Constructor - Keep S3Service for now
    public DocumentProcessingService(DocumentRepository documentRepository, 
                                   S3Service s3Service, // Use S3Service directly
                                   HuggingFaceService huggingFaceService, 
                                   MalwareScanService malwareScanService, 
                                   PolicyEnforcementService policyEnforcementService, 
                                   AuditService auditService) {
        this.documentRepository = documentRepository;
        this.s3Service = s3Service;
        this.huggingFaceService = huggingFaceService;
        this.malwareScanService = malwareScanService;
        this.policyEnforcementService = policyEnforcementService;
        this.auditService = auditService;
        log.info("🎯 DocumentProcessingService CREATED successfully with S3Service");
    }
    
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processDocumentAsync(Long documentId) {
        log.info("🚀 ASYNC PROCESSING STARTED for document ID: {}", documentId);
        
        try {
            log.debug("🔧 DEBUG: Step 1 - Finding document in database...");
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
            
            log.debug("🔧 DEBUG: Step 2 - Document found: {}", document.getOriginalFilename());
            log.debug("🔧 DEBUG: Step 2.1 - S3 Key: {}", document.getS3Key());
            log.debug("🔧 DEBUG: Step 2.2 - Content Type: {}", document.getContentType());
            
            log.debug("🔧 DEBUG: Step 3 - About to update status to SCANNING...");
            document.setStatus(Document.DocumentStatus.SCANNING);
            documentRepository.save(document);
            log.debug("🔧 DEBUG: Step 4 - Status updated successfully");
            
            log.debug("🔧 DEBUG: Step 5 - About to download from S3...");
            log.debug("🔧 DEBUG: Step 5.1 - S3Service instance: {}", s3Service);
            
            byte[] fileContent = s3Service.downloadFile(document.getS3Key());
            log.debug("🔧 DEBUG: Step 6 - File downloaded successfully, size: {} bytes", fileContent.length);
            
            log.debug("🔧 DEBUG: Step 7 - About to start malware scan...");
            log.debug("🔧 DEBUG: Step 7.1 - MalwareScanService instance: {}", malwareScanService);
            
            MalwareScanResult scanResult = malwareScanService.scanFile(fileContent, document.getContentType());
            log.debug("🔧 DEBUG: Step 8 - Malware scan completed - Clean: {}", scanResult.isClean());
            log.debug("🔧 DEBUG: Step 8.1 - Scan details: {}", scanResult.getDetails());
            log.debug("🔧 DEBUG: Step 8.2 - Threats found: {}", scanResult.getThreats());
            
            if (!scanResult.isClean()) {
                log.warn("🔧 DEBUG: Step 9 - Document not clean, quarantining...");
                quarantineDocument(document, "Malware or PII detected: " + scanResult.getDetails());
                return CompletableFuture.completedFuture(null);
            }
            
            log.debug("🔧 DEBUG: Step 10 - Extracting text content...");
            String extractedText = extractTextContent(fileContent, document.getContentType());
            document.setExtractedText(extractedText);
            log.debug("🔧 DEBUG: Step 11 - Text extracted, length: {} characters", extractedText.length());
            log.debug("🔧 DEBUG: Step 11.1 - Text preview: {}", 
                    extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);
            
            // Update status to analyzing
            document.setStatus(Document.DocumentStatus.ANALYZING);
            documentRepository.save(document);
            log.debug("🔧 DEBUG: Step 12 - Status updated to ANALYZING");
            
            log.debug("🔧 DEBUG: Step 13 - About to start LLM analysis...");
            log.debug("🔧 DEBUG: Step 13.1 - HuggingFaceService instance: {}", huggingFaceService);
            
            // Changed from ollamaService to huggingFaceService
            LLMAnalysisResult llmResult = huggingFaceService.analyzeDocument(extractedText, document.getOriginalFilename());
            log.debug("🔧 DEBUG: Step 14 - LLM analysis completed");
            log.debug("🔧 DEBUG: Step 14.1 - Risk Level: {}", llmResult.getRiskLevel());
            log.debug("🔧 DEBUG: Step 14.2 - Risk Summary: {}", llmResult.getRiskSummary());
            log.debug("🔧 DEBUG: Step 14.3 - Detected Flags: {}", llmResult.getDetectedFlags());
            log.debug("🔧 DEBUG: Step 14.4 - Categories: {}", llmResult.getCategories());
            
            document.setLlmAnalysis(llmResult.getAnalysis());
            document.setRiskSummary(llmResult.getRiskSummary());
            document.setDetectedFlags(llmResult.getDetectedFlags());
            document.setCategories(llmResult.getCategories());
            document.setRiskLevel(llmResult.getRiskLevel());
            documentRepository.save(document);
            log.debug("🔧 DEBUG: Step 15 - Document updated with LLM results");
            
            log.debug("🔧 DEBUG: Step 16 - About to start policy enforcement...");
            PolicyEnforcementResult policyResult = policyEnforcementService.enforcePolicy(document);
            log.debug("🔧 DEBUG: Step 17 - Policy enforcement completed - Allowed: {}", policyResult.isAllowed());
            log.debug("🔧 DEBUG: Step 17.1 - Policy reason: {}", policyResult.getReason());
            
            if (!policyResult.isAllowed()) {
                log.warn("🔧 DEBUG: Step 18 - Policy violation, quarantining...");
                quarantineDocument(document, "Policy violation: " + policyResult.getReason());
                return CompletableFuture.completedFuture(null);
            }
            
            // Final approval
            document.setStatus(Document.DocumentStatus.APPROVED);
            documentRepository.save(document);
            log.info("🔧 DEBUG: Step 19 - Document processing COMPLETED successfully - APPROVED");
            
            auditService.logDocumentProcessing(document);
            
        } catch (Exception e) {
            log.error("❌ ERROR processing document ID: {} - Exception: {}", documentId, e.getMessage(), e);
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                document.setStatus(Document.DocumentStatus.REJECTED);
                document.setRiskSummary("Processing failed: " + e.getMessage());
                documentRepository.save(document);
                log.error("🔧 DEBUG: Document status updated to REJECTED due to error");
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private void quarantineDocument(Document document, String reason) {
        log.warn("🚨 QUARANTINING document - ID: {}, Reason: {}", document.getId(), reason);
        document.setStatus(Document.DocumentStatus.QUARANTINED);
        document.setIsQuarantined(true);
        document.setQuarantineReason(reason);
        document.setRiskLevel(Document.RiskLevel.CRITICAL);
        documentRepository.save(document);
        
        auditService.logDocumentQuarantine(document, reason);
        log.warn("🚨 Document quarantined successfully - ID: {}", document.getId());
    }
    
    private String extractTextContent(byte[] fileContent, String contentType) {
        try {
            String extractedText = tika.parseToString(new java.io.ByteArrayInputStream(fileContent));
            log.info("📝 Tika extraction successful for content type: {}", contentType);
            return extractedText;
        } catch (Exception e) {
            log.error("❌ Error extracting text content for type: {}", contentType, e);
            return "";
        }
    }
}