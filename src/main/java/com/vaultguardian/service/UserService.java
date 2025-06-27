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
        log.info("Registering new user: {}", request != null ? request.getUsername() : "null request");
        
        try {
            // Enhanced null checks
            if (request == null) {
                log.error("RegisterRequest is null");
                throw new IllegalArgumentException("Registration request cannot be null");
            }
            
            // Validate input with detailed logging
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty: {}", request.getUsername());
                throw new IllegalArgumentException("Username is required");
            }
            
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                log.error("Email is null or empty: {}", request.getEmail());
                throw new IllegalArgumentException("Email is required");
            }
            
            if (request.getPassword() == null || request.getPassword().length() < 6) {
                log.error("Password is null or too short. Length: {}", 
                    request.getPassword() != null ? request.getPassword().length() : "null");
                throw new IllegalArgumentException("Password must be at least 6 characters long");
            }
            
            // Sanitize inputs
            String username = request.getUsername().trim();
            String email = request.getEmail().trim().toLowerCase();
            
            log.debug("Checking if username exists: {}", username);
            // Check if username already exists with enhanced error handling
            try {
                if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
                    log.warn("Username already exists: {}", username);
                    throw new IllegalArgumentException("Username already exists");
                }
            } catch (Exception e) {
                log.error("Error checking username existence: {}", username, e);
                throw new RuntimeException("Database error while checking username", e);
            }
            
            log.debug("Checking if email exists: {}", email);
            // Check if email already exists with enhanced error handling
            try {
                if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
                    log.warn("Email already exists: {}", email);
                    throw new IllegalArgumentException("Email already exists");
                }
            } catch (Exception e) {
                log.error("Error checking email existence: {}", email, e);
                throw new RuntimeException("Database error while checking email", e);
            }
            
            // Create new user with enhanced null safety
            Set<User.Role> roles = new HashSet<>();
            roles.add(User.Role.USER); // Default role
            
            log.debug("Creating user entity for: {}", username);
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .firstName(request.getFirstName() != null ? request.getFirstName().trim() : "")
                    .lastName(request.getLastName() != null ? request.getLastName().trim() : "")
                    .roles(roles)
                    .isEnabled(true)
                    .isAccountNonLocked(true)
                    .isAccountNonExpired(true)
                    .isCredentialsNonExpired(true)
                    .failedLoginAttempts(0)
                    .build();
            
            log.debug("Saving user to database: {}", username);
            user = userRepository.save(user);
            log.info("User registered successfully: {} with ID: {}", user.getUsername(), user.getId());
            
            return user;
            
        } catch (IllegalArgumentException e) {
            log.warn("Validation error during registration: {}", e.getMessage());
            throw e; // Re-throw validation errors as-is
        } catch (Exception e) {
            log.error("Unexpected error during user registration for username: {}", 
                request != null ? request.getUsername() : "null", e);
            throw new RuntimeException("Failed to register user: " + e.getMessage(), e);
        }
    }
    
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.error("Username is null or empty in findByUsername");
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        try {
            return userRepository.findByUsernameIgnoreCase(username.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } catch (UsernameNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            log.error("Database error while finding user by username: {}", username, e);
            throw new RuntimeException("Database error while finding user", e);
        }
    }
    
    public User findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.error("Email is null or empty in findByEmail");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        
        try {
            return userRepository.findByEmailIgnoreCase(email.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        } catch (UsernameNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            log.error("Database error while finding user by email: {}", email, e);
            throw new RuntimeException("Database error while finding user", e);
        }
    }
    
    @Transactional
    public void updateLastLogin(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Username is null or empty in updateLastLogin");
            return;
        }
        
        try {
            User user = findByUsername(username);
            // FIXED: Use lastLoginAt instead of lastLogin
            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0); // Reset failed attempts on successful login
            userRepository.save(user);
            log.info("Updated last login for user: {}", username);
        } catch (UsernameNotFoundException e) {
            log.warn("Cannot update last login - user not found: {}", username);
        } catch (Exception e) {
            log.error("Failed to update last login for user: {}", username, e);
        }
    }
    
    @Transactional
    public void incrementFailedLoginAttempts(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Username is null or empty in incrementFailedLoginAttempts");
            return;
        }
        
        try {
            User user = findByUsername(username);
            
            Integer currentAttempts = user.getFailedLoginAttempts();
            if (currentAttempts == null) {
                currentAttempts = 0;
            }
            
            user.setFailedLoginAttempts(currentAttempts + 1);
            
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
        if (username == null || username.trim().isEmpty()) {
            log.warn("Username is null or empty in isAccountLocked");
            return false;
        }
        
        try {
            User user = findByUsername(username);
            Boolean isLocked = user.getIsAccountNonLocked();
            return isLocked != null ? !isLocked : false;
        } catch (UsernameNotFoundException e) {
            log.debug("User not found when checking if account is locked: {}", username);
            return false;
        } catch (Exception e) {
            log.error("Error checking if account is locked for user: {}", username, e);
            return false;
        }
    }
    
    @Transactional
    public void unlockAccount(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Username is null or empty in unlockAccount");
            return;
        }
        
        try {
            User user = findByUsername(username);
            user.setIsAccountNonLocked(true);
            user.setFailedLoginAttempts(0);
            user.setLockedAt(null);
            userRepository.save(user);
            log.info("Account unlocked for user: {}", username);
        } catch (UsernameNotFoundException e) {
            log.warn("Cannot unlock account - user not found: {}", username);
        } catch (Exception e) {
            log.error("Failed to unlock account for user: {}", username, e);
        }
    }
    
    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        if (oldPassword == null) {
            throw new IllegalArgumentException("Current password cannot be null");
        }
        
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        
        try {
            User user = findByUsername(username);
            
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            log.info("Password changed for user: {}", username);
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors as-is
        } catch (Exception e) {
            log.error("Failed to change password for user: {}", username, e);
            throw new RuntimeException("Failed to change password", e);
        }
    }
    
    @Transactional
    public void updateUserProfile(String username, String firstName, String lastName, String email) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        try {
            User user = findByUsername(username);
            
            if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
                String trimmedEmail = email.trim().toLowerCase();
                // Check if new email already exists
                if (userRepository.findByEmailIgnoreCase(trimmedEmail).isPresent()) {
                    throw new IllegalArgumentException("Email already exists");
                }
                user.setEmail(trimmedEmail);
            }
            
            if (firstName != null) {
                user.setFirstName(firstName.trim());
            }
            
            if (lastName != null) {
                user.setLastName(lastName.trim());
            }
            
            userRepository.save(user);
            log.info("Profile updated for user: {}", username);
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors as-is
        } catch (Exception e) {
            log.error("Failed to update profile for user: {}", username, e);
            throw new RuntimeException("Failed to update user profile", e);
        }
    }
    
    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Username is null or empty in existsByUsername");
            return false;
        }
        
        try {
            return userRepository.findByUsernameIgnoreCase(username.trim()).isPresent();
        } catch (Exception e) {
            log.error("Error checking if username exists: {}", username, e);
            return false;
        }
    }
    
    public boolean existsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Email is null or empty in existsByEmail");
            return false;
        }
        
        try {
            return userRepository.findByEmailIgnoreCase(email.trim()).isPresent();
        } catch (Exception e) {
            log.error("Error checking if email exists: {}", email, e);
            return false;
        }
    }
}