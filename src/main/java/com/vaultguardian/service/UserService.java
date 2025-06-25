package com.vaultguardian.service;

import com.vaultguardian.dto.RegisterRequest;
import com.vaultguardian.entity.User;
import com.vaultguardian.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional
    public User registerUser(RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());
        
        // Validate input
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        
        // Check if username already exists
        if (userRepository.findByUsernameIgnoreCase(request.getUsername().trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        // Check if email already exists
        if (userRepository.findByEmailIgnoreCase(request.getEmail().trim()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Create new user
        Set<User.Role> roles = new HashSet<>();
        roles.add(User.Role.USER); // Default role
        
        User user = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName() != null ? request.getFirstName().trim() : "")
                .lastName(request.getLastName() != null ? request.getLastName().trim() : "")
                .roles(roles)
                .isEnabled(true)
                .isAccountNonLocked(true)
                .failedLoginAttempts(0)
                .build();
        
        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());
        
        return user;
    }
    
    public User findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
    
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
    
    @Transactional
    public void updateLastLogin(String username) {
        try {
            User user = findByUsername(username);
            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0); // Reset failed attempts on successful login
            userRepository.save(user);
            log.info("Updated last login for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to update last login for user: {}", username, e);
        }
    }
    
    @Transactional
    public void incrementFailedLoginAttempts(String username) {
        try {
            User user = findByUsername(username);
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            
            // Lock account after 5 failed attempts
            if (user.getFailedLoginAttempts() >= 5) {
                user.setIsAccountNonLocked(false);
                user.setLockedAt(LocalDateTime.now());
                log.warn("Account locked due to failed login attempts: {}", username);
            }
            
            userRepository.save(user);
            log.warn("Incremented failed login attempts for user: {} (total: {})", 
                    username, user.getFailedLoginAttempts());
            
        } catch (UsernameNotFoundException e) {
            // User doesn't exist, but don't reveal this information
            log.warn("Failed login attempt for non-existent user: {}", username);
        } catch (Exception e) {
            log.error("Failed to increment login attempts for user: {}", username, e);
        }
    }
    
    public boolean isAccountLocked(String username) {
        try {
            User user = findByUsername(username);
            return !user.getIsAccountNonLocked();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }
    
    @Transactional
    public void unlockAccount(String username) {
        try {
            User user = findByUsername(username);
            user.setIsAccountNonLocked(true);
            user.setFailedLoginAttempts(0);
            user.setLockedAt(null);
            userRepository.save(user);
            log.info("Account unlocked for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to unlock account for user: {}", username, e);
        }
    }
    
    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = findByUsername(username);
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", username);
    }
    
    @Transactional
    public void updateUserProfile(String username, String firstName, String lastName, String email) {
        User user = findByUsername(username);
        
        if (email != null && !email.equals(user.getEmail())) {
            // Check if new email already exists
            if (userRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(email.trim().toLowerCase());
        }
        
        if (firstName != null) {
            user.setFirstName(firstName.trim());
        }
        
        if (lastName != null) {
            user.setLastName(lastName.trim());
        }
        
        userRepository.save(user);
        log.info("Profile updated for user: {}", username);
    }
    
    public boolean existsByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username).isPresent();
    }
    
    public boolean existsByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).isPresent();
    }
}