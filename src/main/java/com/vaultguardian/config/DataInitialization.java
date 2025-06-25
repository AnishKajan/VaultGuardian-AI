package com.vaultguardian.config;

import com.vaultguardian.entity.User;
import com.vaultguardian.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitialization implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        initializeAdminUser();
    }
    
    private void initializeAdminUser() {
        try {
            if (!userRepository.existsByUsernameIgnoreCase("admin")) {
                log.info("Creating default admin user...");
                
                Set<User.Role> adminRoles = new HashSet<>();
                adminRoles.add(User.Role.ADMIN);
                adminRoles.add(User.Role.USER);
                
                User admin = User.builder()
                    .username("admin")
                    .email("admin@vaultguardian.com")
                    .password(passwordEncoder.encode("admin123"))
                    .firstName("System")
                    .lastName("Administrator")
                    .roles(adminRoles)
                    .isEnabled(true)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .failedLoginAttempts(0)
                    .build();
                
                userRepository.save(admin);
                log.info("✅ Default admin user created successfully");
                log.info("   Username: admin");
                log.info("   Password: admin123");
            } else {
                log.info("Admin user already exists, skipping creation");
            }
        } catch (Exception e) {
            log.error("❌ Failed to create admin user", e);
        }
    }
}