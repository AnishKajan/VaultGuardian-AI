package com.vaultguardian.dto;

import com.vaultguardian.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponseDto {
    private Long id;
    private String filename;
    private String originalFilename;    // ADDED
    private String contentType;
    private Long fileSize;
    private Document.DocumentStatus status;
    private Document.RiskLevel riskLevel;
    private String sha256Hash;
    private String riskSummary;
    private List<String> detectedFlags;
    private List<String> categories;
    private Boolean isQuarantined;
    private String quarantineReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAccessedAt;
    private String uploadedBy;
}