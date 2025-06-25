package com.vaultguardian.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
    
    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;
    
    @Column(name = "is_account_non_expired")
    @Builder.Default
    private Boolean isAccountNonExpired = true;
    
    @Column(name = "is_account_non_locked")
    @Builder.Default
    private Boolean isAccountNonLocked = true;
    
    @Column(name = "is_credentials_non_expired")
    @Builder.Default
    private Boolean isCredentialsNonExpired = true;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // UserDetails interface methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return isAccountNonExpired != null ? isAccountNonExpired : true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        boolean fieldValue = isAccountNonLocked != null ? isAccountNonLocked : true;
        boolean notCurrentlyLocked = (lockedUntil == null || LocalDateTime.now().isAfter(lockedUntil));
        return fieldValue && notCurrentlyLocked;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return isCredentialsNonExpired != null ? isCredentialsNonExpired : true;
    }
    
    @Override
    public boolean isEnabled() {
        return isEnabled != null ? isEnabled : true;
    }
    
    // Helper methods for compatibility with existing code
    public Role getPrimaryRole() {
        return roles.isEmpty() ? Role.USER : roles.iterator().next();
    }
    
    public Role getRole() {
        return getPrimaryRole();
    }
    
    public void addRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }
    
    public void removeRole(Role role) {
        if (this.roles != null) {
            this.roles.remove(role);
        }
    }
    
    public boolean hasRole(Role role) {
        return this.roles != null && this.roles.contains(role);
    }
    
    // Compatibility methods for AuthController and UserRepository
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLoginAt = lastLogin;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLoginAt;
    }
    
    // Initialize default role if none set
    @PrePersist
    protected void onCreate() {
        if (roles == null || roles.isEmpty()) {
            roles = new HashSet<>();
            roles.add(Role.USER);
        }
    }
    
    // Inner Role enum
    public enum Role {
        USER,
        ADMIN,
        SECURITY_OFFICER,
        AUDITOR
    }
}