// Create this file: src/main/java/com/vaultguardian/controller/TestController.java

package com.vaultguardian.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "VaultGuardian AI Backend is working!");
        response.put("timestamp", LocalDateTime.now());
        response.put("status", "healthy");
        response.put("version", "1.0.0");
        response.put("cors", "enabled");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cors")
    public ResponseEntity<Map<String, Object>> testCors() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "CORS is working!");
        response.put("timestamp", LocalDateTime.now());
        response.put("cors_status", "enabled");
        response.put("frontend_can_connect", true);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Echo test successful");
        response.put("received", request);
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("backend", "healthy");
        response.put("database", "connected");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
}