package com.vaultguardian.config;

import com.vaultguardian.service.AzureBlobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StorageConfiguration {
    
    @Value("${storage.provider:azure}")
    private String storageProvider;
    
    @Autowired(required = false)
    private AzureBlobService azureBlobService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeStorage() {
        log.info("🚀 Initializing storage provider: {}", storageProvider);
        
        try {
            if ("azure".equals(storageProvider) && azureBlobService != null) {
                log.info("🔵 Testing Azure Blob Storage connection...");
                azureBlobService.initializeContainer();
                log.info("✅ Azure Blob Storage initialized successfully");
            } else if ("azure".equals(storageProvider)) {
                log.error("❌ Azure storage provider configured but AzureBlobService not available");
            } else {
                log.warn("⚠️ Unknown storage provider: {}", storageProvider);
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize storage provider: {}", storageProvider, e);
            // Don't fail startup, just log the error
        }
    }
}