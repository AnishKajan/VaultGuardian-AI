package com.vaultguardian.service;

import com.vaultguardian.entity.Document;
import com.vaultguardian.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    public void logDocumentUpload(User user, Document document) {
        log.info("AUDIT: User '{}' uploaded document '{}' (ID: {}, Size: {} bytes)", 
                user.getUsername(), 
                document.getOriginalFilename(), 
                document.getId(), 
                document.getFileSize());
    }
    
    public void logDocumentProcessing(Document document) {
        log.info("AUDIT: Document '{}' (ID: {}) processing completed. Status: {}, Risk Level: {}, Flags: {}", 
                document.getOriginalFilename(),
                document.getId(),
                document.getStatus(),
                document.getRiskLevel(),
                document.getDetectedFlags() != null ? document.getDetectedFlags().size() : 0);
    }
    
    public void logDocumentQuarantine(Document document, String reason) {
        log.warn("AUDIT: Document '{}' (ID: {}) QUARANTINED. Reason: {}", 
                document.getOriginalFilename(),
                document.getId(),
                reason);
    }
    
    public void logDocumentAccess(User user, Document document) {
        log.info("AUDIT: User '{}' accessed document '{}' (ID: {})", 
                user.getUsername(),
                document.getOriginalFilename(),
                document.getId());
    }
    
    public void logDocumentDownload(User user, Document document) {
        log.info("AUDIT: User '{}' downloaded document '{}' (ID: {})", 
                user.getUsername(),
                document.getOriginalFilename(),
                document.getId());
    }
    
    public void logDocumentDeletion(User user, Document document) {
        log.warn("AUDIT: User '{}' deleted document '{}' (ID: {})", 
                user.getUsername(),
                document.getOriginalFilename(),
                document.getId());
    }
    
    public void logUserLogin(String username, String ipAddress, boolean successful) {
        if (successful) {
            log.info("AUDIT: User '{}' logged in successfully from IP: {}", username, ipAddress);
        } else {
            log.warn("AUDIT: Failed login attempt for user '{}' from IP: {}", username, ipAddress);
        }
    }
    
    public void logUserLogout(String username) {
        log.info("AUDIT: User '{}' logged out", username);
    }
    
    public void logSecurityEvent(String event, String details) {
        log.warn("AUDIT: SECURITY EVENT - {}: {}", event, details);
    }
    
    public void logSystemEvent(String event, String details) {
        log.info("AUDIT: SYSTEM EVENT - {}: {}", event, details);
    }
    
    public void logPolicyViolation(Document document, String policy, String details) {
        log.warn("AUDIT: POLICY VIOLATION - Document '{}' (ID: {}) violated policy '{}': {}", 
                document.getOriginalFilename(),
                document.getId(),
                policy,
                details);
    }
    
    public void logAdminAction(User admin, String action, String target) {
        log.info("AUDIT: Admin '{}' performed action '{}' on target '{}'", 
                admin.getUsername(),
                action,
                target);
    }
}