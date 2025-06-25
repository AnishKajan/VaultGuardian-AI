package com.vaultguardian.service;

import com.vaultguardian.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMAnalysisResult {
    private String analysis;
    private String riskSummary;
    private List<String> detectedFlags;
    private List<String> categories;
    private List<String> recommendations;
    private Integer confidence;
    private Document.RiskLevel riskLevel;
}