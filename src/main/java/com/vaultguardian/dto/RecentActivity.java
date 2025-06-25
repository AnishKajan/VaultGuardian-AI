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
public class RecentActivity {
    private String action;
    private String documentName;
    private String username;
    private LocalDateTime timestamp;
    private Document.RiskLevel riskLevel;
}