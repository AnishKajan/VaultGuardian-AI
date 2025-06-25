package com.vaultguardian.dto;

import com.vaultguardian.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSummaryDto {
    private Long id;
    private String filename;
    private String originalFilename;    // ADDED
    private Long fileSize;
    private Document.DocumentStatus status;
    private Document.RiskLevel riskLevel;
    private Integer flagCount;
    private Boolean isQuarantined;
    private LocalDateTime createdAt;
}