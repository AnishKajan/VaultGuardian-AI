package com.vaultguardian.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    @Autowired
    private DataSource dataSource;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "VaultGuardian AI");
        health.put("version", "1.0.0");
        
        // Check database connection
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", "UP");
            health.put("database_status", "Connected");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("database_error", e.getMessage());
            health.put("status", "DOWN");
        }
        
        // Check storage service (basic check)
        health.put("storage", "CONFIGURED");
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}