package com.vaultguardian.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyEnforcementResult {
    private boolean isAllowed;
    private String reason;
    private List<String> violatedPolicies;
    private String action; // ALLOW, QUARANTINE, REJECT
    private String recommendation;
}