package com.vaultguardian.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AzureConfig {
    
    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
    public BlobServiceClient blobServiceClient(
            @Value("${azure.storage.account-name:}") String accountName,
            @Value("${azure.storage.account-key:}") String accountKey,
            @Value("${azure.storage.endpoint:}") String endpoint) {
        
        try {
            log.info("🔵 Initializing Azure Blob Service Client...");
            
            if (accountName == null || accountName.trim().isEmpty()) {
                log.error("❌ Azure Storage account name is missing");
                throw new IllegalStateException("AZURE_STORAGE_ACCOUNT_NAME environment variable is required");
            }
            
            if (accountKey == null || accountKey.trim().isEmpty()) {
                log.error("❌ Azure Storage account key is missing");
                throw new IllegalStateException("AZURE_STORAGE_ACCOUNT_KEY environment variable is required");
            }
            
            log.info("✅ Azure credentials found - Account: {}", accountName);
            
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                accountName.trim(), 
                accountKey.trim()
            );
            
            String connectionEndpoint;
            if (endpoint != null && !endpoint.trim().isEmpty()) {
                connectionEndpoint = endpoint.trim();
            } else {
                connectionEndpoint = String.format("https://%s.blob.core.windows.net", accountName.trim());
            }
            
            log.info("🔗 Connecting to endpoint: {}", connectionEndpoint);
            
            BlobServiceClient client = new BlobServiceClientBuilder()
                    .endpoint(connectionEndpoint)
                    .credential(credential)
                    .buildClient();
            
            log.info("✅ Azure Blob Service Client created successfully");
            return client;
            
        } catch (Exception e) {
            log.error("❌ Failed to create Azure Blob Service Client: {}", e.getMessage(), e);
            throw new IllegalStateException("Azure Blob Storage initialization failed", e);
        }
    }
}