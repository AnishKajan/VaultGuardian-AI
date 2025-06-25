package com.vaultguardian.dto;

import com.vaultguardian.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskDistribution {
    private Document.RiskLevel riskLevel;
    private Long count;
    private Double percentage;
}