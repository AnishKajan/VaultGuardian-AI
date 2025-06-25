package com.vaultguardian.controller;

import com.vaultguardian.dto.LoginRequest;
import com.vaultguardian.dto.LoginResponse;
import com.vaultguardian.dto.RegisterRequest;
import com.vaultguardian.dto.UserResponse;
import com.vaultguardian.entity.User;
import com.vaultguardian.repository.UserRepository;
import com.vaultguardian.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
// REMOVED: @CrossOrigin annotation - let SecurityConfig handle CORS
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.info("Login attempt for username: {}", loginRequest.getUsername());
            
            // Check if user exists first
            User user = userRepository.findByUsernameIgnoreCase(loginRequest.getUsername())
                .orElse(null);
            
            if (user == null) {
                log.warn("Account not found for username: {}", loginRequest.getUsername());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Account Not Found");
                error.put("message", "No account found with this username");
                error.put("errorCode", "ACCOUNT_NOT_FOUND");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            
            // Update last login time
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            
            // Generate JWT token
            String token = jwtTokenUtil.generateToken(userDetails);
            
            // FIXED: Add missing fields that frontend expects
            LoginResponse response = LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName()) // Add this
                .lastName(user.getLastName())   // Add this
                .roles(user.getRoles().stream() // Add this
                    .map(User.Role::name)
                    .collect(Collectors.toSet()))
                .role(user.getPrimaryRole().name())
                .expiresIn(86400L) // 24 hours in seconds
                .build();
            
            log.info("Login successful for user: {}", loginRequest.getUsername());
            return ResponseEntity.ok(response);
            
        } catch (BadCredentialsException e) {
            log.warn("Incorrect password for username: {}", loginRequest.getUsername());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Incorrect Password");
            error.put("message", "The password you entered is incorrect");
            error.put("errorCode", "INCORRECT_PASSWORD");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            
        } catch (Exception e) {
            log.error("Login error for username: {}", loginRequest.getUsername(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Login Failed");
            error.put("message", "Unable to process login request");
            error.put("errorCode", "LOGIN_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            log.info("Registration attempt for username: {}", registerRequest.getUsername());
            
            // Check if username already exists
            if (userRepository.existsByUsernameIgnoreCase(registerRequest.getUsername())) {
                log.warn("Username already exists: {}", registerRequest.getUsername());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Account Already Exists");
                error.put("message", "An account with this username already exists");
                error.put("errorCode", "USERNAME_EXISTS");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            
            // Check if email already exists
            if (userRepository.existsByEmailIgnoreCase(registerRequest.getEmail())) {
                log.warn("Email already exists: {}", registerRequest.getEmail());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Email Already Registered");
                error.put("message", "An account with this email already exists");
                error.put("errorCode", "EMAIL_EXISTS");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            
            // Create roles set FIRST
            Set<User.Role> userRoles = new HashSet<>();
            userRoles.add(User.Role.USER);
            
            log.debug("Creating user with roles: {}", userRoles);
            
            // Create new user with explicit field setting
            User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .roles(userRoles)  // Set roles explicitly
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isAccountNonLocked(true)
                .isCredentialsNonExpired(true)
                .failedLoginAttempts(0)
                .build();
            
            log.debug("Created user object: {}", user.getUsername());
            log.debug("User roles: {}", user.getRoles());
            
            User savedUser = userRepository.save(user);
            log.info("✅ User registered successfully with ID: {}", savedUser.getId());
            
            UserResponse response = UserResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName()) // Add this
                .lastName(savedUser.getLastName())   // Add this
                .role(savedUser.getPrimaryRole().name())
                .roles(savedUser.getRoles().stream()
                    .map(User.Role::name)
                    .collect(Collectors.toSet()))
                .createdAt(savedUser.getCreatedAt())
                .build();
            
            log.info("Registration successful for user: {}", registerRequest.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Registration error for username: {} - Exception: {}", 
                     registerRequest.getUsername(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Registration Failed");
            error.put("message", "Unable to create account: " + e.getMessage());
            error.put("errorCode", "REGISTRATION_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Unauthorized");
                error.put("message", "Access token is missing or invalid");
                error.put("errorCode", "UNAUTHORIZED");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            String username = authentication.getName();
            User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName()) // Add this
                .lastName(user.getLastName())   // Add this
                .role(user.getPrimaryRole().name())
                .roles(user.getRoles().stream()
                    .map(User.Role::name)
                    .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting current user", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", "Failed to get user information");
            error.put("errorCode", "USER_INFO_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}