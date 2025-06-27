package com.vaultguardian.controller;

import com.vaultguardian.dto.LoginRequest;
import com.vaultguardian.dto.LoginResponse;
import com.vaultguardian.dto.RegisterRequest;
import com.vaultguardian.dto.UserResponse;
import com.vaultguardian.entity.User;
import com.vaultguardian.repository.UserRepository;
import com.vaultguardian.security.JwtTokenUtil;
import com.vaultguardian.service.UserService;
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
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.info("Login attempt for username: {}", loginRequest != null ? loginRequest.getUsername() : "null");

            // Enhanced null checks
            if (loginRequest == null) {
                log.error("LoginRequest is null");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Login request cannot be empty");
                error.put("errorCode", "INVALID_REQUEST");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Username is required");
                error.put("errorCode", "MISSING_USERNAME");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            if (loginRequest.getPassword() == null || loginRequest.getPassword().isEmpty()) {
                log.error("Password is null or empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Password is required");
                error.put("errorCode", "MISSING_PASSWORD");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            String username = loginRequest.getUsername().trim();
            
            User user = userRepository.findByUsernameIgnoreCase(username)
                    .orElse(null);

            if (user == null) {
                log.warn("Account not found for username: {}", username);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Account Not Found");
                error.put("message", "No account found with this username");
                error.put("errorCode", "ACCOUNT_NOT_FOUND");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            // Check if account is locked
            if (user.getIsAccountNonLocked() != null && !user.getIsAccountNonLocked()) {
                log.warn("Account is locked for username: {}", username);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Account Locked");
                error.put("message", "Your account has been locked due to too many failed login attempts");
                error.put("errorCode", "ACCOUNT_LOCKED");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // FIXED: Update last login with proper field name
            try {
                user.setLastLoginAt(LocalDateTime.now()); // Using lastLoginAt, not lastLogin
                user.setFailedLoginAttempts(0); // Reset failed attempts
                userRepository.save(user);
            } catch (Exception e) {
                log.warn("Failed to update last login for user: {}", username, e);
                // Don't fail the login for this
            }

            String token = jwtTokenUtil.generateToken(userDetails);

            // Build response with null safety
            Set<String> roleNames = user.getRoles() != null ? 
                user.getRoles().stream().map(User.Role::name).collect(Collectors.toSet()) : 
                new HashSet<>();

            String primaryRole = "USER"; // Default role
            try {
                if (user.getPrimaryRole() != null) {
                    primaryRole = user.getPrimaryRole().name();
                }
            } catch (Exception e) {
                log.warn("Error getting primary role for user: {}, using default", username, e);
            }

            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName() != null ? user.getFirstName() : "")
                    .lastName(user.getLastName() != null ? user.getLastName() : "")
                    .roles(roleNames)
                    .role(primaryRole)
                    .expiresIn(86400L)
                    .build();

            log.info("Login successful for user: {}", username);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            String username = loginRequest != null ? loginRequest.getUsername() : "unknown";
            log.warn("Incorrect password for username: {}", username);
            
            // Increment failed login attempts
            try {
                userService.incrementFailedLoginAttempts(username);
            } catch (Exception ex) {
                log.warn("Failed to increment login attempts for user: {}", username, ex);
            }
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Incorrect Password");
            error.put("message", "The password you entered is incorrect");
            error.put("errorCode", "INCORRECT_PASSWORD");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);

        } catch (Exception e) {
            String username = loginRequest != null ? loginRequest.getUsername() : "unknown";
            log.error("Login error for username: {}", username, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Login Failed");
            error.put("message", "Unable to process login request: " + e.getMessage());
            error.put("errorCode", "LOGIN_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            log.info("Registration attempt for username: {}", 
                registerRequest != null ? registerRequest.getUsername() : "null");

            // Enhanced null checks
            if (registerRequest == null) {
                log.error("RegisterRequest is null");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Registration request cannot be empty");
                error.put("errorCode", "INVALID_REQUEST");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            // Validate required fields
            if (registerRequest.getUsername() == null || registerRequest.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Username is required");
                error.put("errorCode", "MISSING_USERNAME");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            if (registerRequest.getEmail() == null || registerRequest.getEmail().trim().isEmpty()) {
                log.error("Email is null or empty");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Email is required");
                error.put("errorCode", "MISSING_EMAIL");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            if (registerRequest.getPassword() == null || registerRequest.getPassword().length() < 6) {
                log.error("Password is null or too short");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Request");
                error.put("message", "Password must be at least 6 characters long");
                error.put("errorCode", "INVALID_PASSWORD");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            String username = registerRequest.getUsername().trim();
            String email = registerRequest.getEmail().trim();

            // Check for existing username
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                log.warn("Username already exists: {}", username);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Account Already Exists");
                error.put("message", "An account with this username already exists");
                error.put("errorCode", "USERNAME_EXISTS");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            // Check for existing email
            if (userRepository.existsByEmailIgnoreCase(email)) {
                log.warn("Email already exists: {}", email);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Email Already Registered");
                error.put("message", "An account with this email already exists");
                error.put("errorCode", "EMAIL_EXISTS");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            // Use UserService for registration with enhanced error handling
            User savedUser;
            try {
                savedUser = userService.registerUser(registerRequest);
            } catch (IllegalArgumentException e) {
                log.warn("Validation error during registration: {}", e.getMessage());
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Validation Error");
                error.put("message", e.getMessage());
                error.put("errorCode", "VALIDATION_ERROR");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            // Build response with null safety
            Set<String> roleNames = savedUser.getRoles() != null ? 
                savedUser.getRoles().stream().map(User.Role::name).collect(Collectors.toSet()) : 
                new HashSet<>();

            String primaryRole = "USER"; // Default role
            try {
                if (savedUser.getPrimaryRole() != null) {
                    primaryRole = savedUser.getPrimaryRole().name();
                }
            } catch (Exception e) {
                log.warn("Error getting primary role for user: {}, using default", username, e);
            }

            UserResponse response = UserResponse.builder()
                    .id(savedUser.getId())
                    .username(savedUser.getUsername())
                    .email(savedUser.getEmail())
                    .firstName(savedUser.getFirstName() != null ? savedUser.getFirstName() : "")
                    .lastName(savedUser.getLastName() != null ? savedUser.getLastName() : "")
                    .role(primaryRole)
                    .roles(roleNames)
                    .createdAt(savedUser.getCreatedAt())
                    .build();

            log.info("Registration successful for user: {}", username);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            String username = registerRequest != null ? registerRequest.getUsername() : "unknown";
            log.error("Registration error for username: {} - Exception: {}", username, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Registration Failed");
            error.put("message", "Unable to create account: " + e.getMessage());
            error.put("errorCode", "REGISTRATION_ERROR");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access attempt to /me endpoint");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Unauthorized");
                error.put("message", "Access token is missing or invalid");
                error.put("errorCode", "UNAUTHORIZED");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String username = authentication.getName();
            if (username == null || username.trim().isEmpty()) {
                log.error("Username is null or empty from authentication");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid Token");
                error.put("message", "Authentication token is invalid");
                error.put("errorCode", "INVALID_TOKEN");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            User user = userRepository.findByUsernameIgnoreCase(username.trim())
                    .orElse(null);

            if (user == null) {
                log.error("User not found for authenticated username: {}", username);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "User Not Found");
                error.put("message", "Authenticated user no longer exists");
                error.put("errorCode", "USER_NOT_FOUND");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            // Build response with null safety
            Set<String> roleNames = user.getRoles() != null ? 
                user.getRoles().stream().map(User.Role::name).collect(Collectors.toSet()) : 
                new HashSet<>();

            String primaryRole = "USER"; // Default role
            try {
                if (user.getPrimaryRole() != null) {
                    primaryRole = user.getPrimaryRole().name();
                }
            } catch (Exception e) {
                log.warn("Error getting primary role for user: {}, using default", username, e);
            }

            UserResponse response = UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName() != null ? user.getFirstName() : "")
                    .lastName(user.getLastName() != null ? user.getLastName() : "")
                    .role(primaryRole)
                    .roles(roleNames)
                    .createdAt(user.getCreatedAt())
                    .lastLogin(user.getLastLoginAt()) // FIXED: Use lastLoginAt
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting current user", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", "Failed to get user information: " + e.getMessage());
            error.put("errorCode", "USER_INFO_ERROR");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}