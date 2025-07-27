package com.vaultguardian.service;

import com.vaultguardian.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyEnforcementService {
    
    @Value("${security.policy.max-file-size:52428800}") // 50MB default
    private long maxFileSize;
    
    @Value("${security.policy.quarantine-high-risk:true}")
    private boolean quarantineHighRisk;
    
    @Value("${security.policy.block-pii:true}")
    private boolean blockPII;
    
    @Value("${security.policy.block-credentials:true}")
    private boolean blockCredentials;
    
    @Value("${security.policy.max-risk-flags:3}")
    private int maxRiskFlags;
    
    // Blocked file categories
    private static final List<String> BLOCKED_CATEGORIES = Arrays.asList(
        "Malware",
        "Executable",
        "Script",
        "Suspicious"
    );
    
    // High-risk flags that trigger automatic quarantine
    private static final List<String> HIGH_RISK_FLAGS = Arrays.asList(
        "Credit Card Number",
        "Social Security Number", 
        "Hardcoded Password",
        "API Key Exposure",
        "Malware",
        "Executable File",
        "Script Injection",
        "SQL Injection"
    );
    
    // PII-related flags
    private static final List<String> PII_FLAGS = Arrays.asList(
        "Credit Card Number",
        "Social Security Number",
        "Phone Number",
        "Email Address",
        "Passport Number",
        "Driver's License",
        "PII"
    );
    
    // Credential-related flags  
    private static final List<String> CREDENTIAL_FLAGS = Arrays.asList(
        "Hardcoded Password",
        "API Key Exposure",
        "Secret Exposure",
        "Database Credentials",
        "Azure Credentials",
        "Private Key"
    );
    
    public PolicyEnforcementResult enforcePolicy(Document document) {
        log.info("Enforcing security policy for document ID: {}", document.getId());
        
        List<String> violatedPolicies = new ArrayList<>();
        String action = "ALLOW"; // Default to ALLOW for Azure storage
        String reason = "";
        String recommendation = "";
        
        try {
            // 1. Check file size policy - REJECT only for oversized files
            if (document.getFileSize() > maxFileSize) {
                violatedPolicies.add("File Size Limit Exceeded");
                action = "REJECT";
                reason = String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)", 
                       document.getFileSize(), maxFileSize);
            }
            
            // 2. Check for blocked categories - REJECT malware/executables
            String blockedCategory = checkBlockedCategories(document.getCategories());
            if (blockedCategory != null) {
                violatedPolicies.add("Blocked Content Category");
                action = "REJECT";
                reason = "Document category '" + blockedCategory + "' is not allowed";
            }
            
            // 3. Check credentials policy - REJECT exposed credentials
            if (blockCredentials && containsCredentials(document.getDetectedFlags())) {
                violatedPolicies.add("Credential Exposure Policy");
                action = "REJECT";
                reason = "Document contains exposed credentials or secrets";
            }
            
            // 4. For HIGH/CRITICAL risk - QUARANTINE but STORE in Azure
            if (quarantineHighRisk && isHighRiskLevel(document.getRiskLevel())) {
                violatedPolicies.add("High Risk Content");
                if (!"REJECT".equals(action)) {
                    action = "QUARANTINE";
                    reason = "Document contains high-risk content - stored but quarantined for manual review";
                }
            }
            
            // 5. Check PII policy - QUARANTINE but STORE
            if (blockPII && containsPII(document.getDetectedFlags())) {
                violatedPolicies.add("PII Detection Policy");
                if (!"REJECT".equals(action)) {
                    action = "QUARANTINE";
                    reason = "Document contains PII - stored but quarantined for compliance review";
                }
            }
            
            // 6. Check maximum risk flags policy - QUARANTINE but STORE
            if (document.getDetectedFlags() != null && 
                document.getDetectedFlags().size() > maxRiskFlags) {
                violatedPolicies.add("Maximum Risk Flags Exceeded");
                if (!"REJECT".equals(action)) {
                    action = "QUARANTINE";
                    reason = String.format("Document has %d risk flags - stored but requires review", 
                           document.getDetectedFlags().size());
                }
            }
            
            // 7. Check for critical risk flags - QUARANTINE but STORE
            if (containsCriticalFlags(document.getDetectedFlags())) {
                violatedPolicies.add("Critical Security Risk");
                if (!"REJECT".equals(action)) {
                    action = "QUARANTINE";
                    reason = "Document contains critical security risks - stored but quarantined";
                }
            }
            
            // IMPORTANT: Documents are STORED in Azure unless explicitly REJECTED
            // QUARANTINE means: stored in Azure + flagged for manual review
            // ALLOW means: stored in Azure + immediately accessible
            // REJECT means: not stored (only for malware, oversized, credentials)
            
            recommendation = generateRecommendation(action, violatedPolicies);
            
            // isAllowed = true means file gets stored in Azure
            boolean isAllowed = !"REJECT".equals(action);
            
            log.info("Policy enforcement completed for document ID: {}. Action: {}, Stored in Azure: {}", 
                    document.getId(), action, isAllowed);
            
            return PolicyEnforcementResult.builder()
                    .isAllowed(isAllowed)
                    .reason(reason.isEmpty() ? "Document stored in Azure Blob Storage" : reason)
                    .violatedPolicies(violatedPolicies)
                    .action(action)
                    .recommendation(recommendation)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error during policy enforcement for document ID: " + document.getId(), e);
            return PolicyEnforcementResult.builder()
                    .isAllowed(true) // Store in Azure even on errors, quarantine for manual review
                    .reason("Policy enforcement error - document quarantined for manual review")
                    .violatedPolicies(Arrays.asList("System Error"))
                    .action("QUARANTINE")
                    .recommendation("Manual review required due to system error")
                    .build();
        }
    }
    
    private boolean isHighRiskLevel(Document.RiskLevel riskLevel) {
        return riskLevel == Document.RiskLevel.HIGH || riskLevel == Document.RiskLevel.CRITICAL;
    }
    
    private String checkBlockedCategories(List<String> categories) {
        if (categories == null) return null;
        
        for (String category : categories) {
            if (BLOCKED_CATEGORIES.contains(category)) {
                return category;
            }
        }
        return null;
    }
    
    private boolean containsPII(List<String> detectedFlags) {
        if (detectedFlags == null) return false;
        
        return detectedFlags.stream()
                .anyMatch(PII_FLAGS::contains);
    }
    
    private boolean containsCredentials(List<String> detectedFlags) {
        if (detectedFlags == null) return false;
        
        return detectedFlags.stream()
                .anyMatch(CREDENTIAL_FLAGS::contains);
    }
    
    private boolean containsCriticalFlags(List<String> detectedFlags) {
        if (detectedFlags == null) return false;
        
        return detectedFlags.stream()
                .anyMatch(HIGH_RISK_FLAGS::contains);
    }
    
    private String generateRecommendation(String action, List<String> violatedPolicies) {
        switch (action) {
            case "ALLOW":
                return "Document approved and stored in Azure Blob Storage. Safe for access and sharing.";
            case "QUARANTINE":
                return "Document stored in Azure Blob Storage but quarantined for security review. " +
                       "Contact security team before sharing or downloading.";
            case "REJECT":
                if (violatedPolicies.contains("Credential Exposure Policy")) {
                    return "Document NOT stored. Remove all credentials and secrets before re-uploading. " +
                           "Use environment variables or secure vaults for sensitive data.";
                } else if (violatedPolicies.contains("File Size Limit Exceeded")) {
                    return "Document NOT stored. Reduce file size or split into smaller files before re-uploading.";
                } else if (violatedPolicies.contains("Blocked Content Category")) {
                    return "Document NOT stored. This file type is not permitted. Contact administrator " +
                           "if business justification exists.";
                } else {
                    return "Document NOT stored. Address security issues identified before re-uploading.";
                }
            default:
                return "Contact system administrator for guidance.";
        }
    }
    
    public boolean isContentTypeAllowed(String contentType) {
        if (contentType == null) return false;
        
        // Allow common document and media types
        return contentType.startsWith("text/") ||
               contentType.startsWith("image/") ||
               contentType.equals("application/pdf") ||
               contentType.equals("application/msword") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               contentType.equals("application/vnd.ms-excel") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               contentType.equals("application/vnd.ms-powerpoint") ||
               contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
               contentType.equals("application/json") ||
               contentType.equals("application/xml") ||
               contentType.equals("application/zip") ||
               contentType.equals("application/x-rar-compressed");
    }
    
    public PolicyEnforcementResult validateUpload(String filename, String contentType, long fileSize) {
        List<String> violatedPolicies = new ArrayList<>();
        String action = "ALLOW";
        String reason = "";
        
        // Check file size
        if (fileSize > maxFileSize) {
            violatedPolicies.add("File Size Limit Exceeded");
            action = "REJECT";
            reason = "File size exceeds maximum allowed limit";
        }
        
        // Check content type
        if (!isContentTypeAllowed(contentType)) {
            violatedPolicies.add("Unsupported File Type");
            action = "REJECT";
            reason = "File type not allowed by security policy";
        }
        
        // Check filename for suspicious patterns
        if (filename != null && containsSuspiciousFilename(filename)) {
            violatedPolicies.add("Suspicious Filename");
            action = "QUARANTINE";
            reason = "Filename contains suspicious patterns";
        }
        
        return PolicyEnforcementResult.builder()
                .isAllowed("ALLOW".equals(action))
                .reason(reason.isEmpty() ? "Upload validation passed" : reason)
                .violatedPolicies(violatedPolicies)
                .action(action)
                .recommendation(generateRecommendation(action, violatedPolicies))
                .build();
    }
    
    private boolean containsSuspiciousFilename(String filename) {
        String lower = filename.toLowerCase();
        return lower.contains("..") ||
               lower.contains("script") ||
               lower.contains("payload") ||
               lower.contains("exploit") ||
               lower.contains("malware") ||
               lower.endsWith(".exe") ||
               lower.endsWith(".bat") ||
               lower.endsWith(".cmd") ||
               lower.endsWith(".scr") ||
               lower.endsWith(".vbs") ||
               lower.endsWith(".js") ||
               lower.endsWith(".ps1");
    }
}