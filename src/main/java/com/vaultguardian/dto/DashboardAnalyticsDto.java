package com.vaultguardian.dto;

import com.vaultguardian.dto.RiskDistribution;
import com.vaultguardian.dto.CategoryDistribution;
import com.vaultguardian.dto.RecentActivity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardAnalyticsDto {
    private Long totalDocuments;
    private Long documentsToday;
    private Long quarantinedDocuments;
    private Long highRiskDocuments;
    private List<RiskDistribution> riskDistribution;
    private List<CategoryDistribution> categoryDistribution;
    private List<RecentActivity> recentActivity;
}