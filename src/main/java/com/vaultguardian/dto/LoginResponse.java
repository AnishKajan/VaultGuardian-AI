package com.vaultguardian.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String username;
    private String email;
    private String firstName;    // ADDED
    private String lastName;     // ADDED
    private String role;
    private Set<String> roles;   // ADDED
    private Long expiresIn;
}