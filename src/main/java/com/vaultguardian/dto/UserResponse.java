package com.vaultguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;      // ADDED
    private String lastName;       // ADDED
    private String role;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}