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
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure", matchIfMissing = false)
@Slf4j
public class AzureConfig {
    
    @Value("${azure.storage.account-name:}")
    private String accountName;
    
    @Value("${azure.storage.account-key:}")
    private String accountKey;
    
    @Value("${azure.storage.endpoint:}")
    private String endpoint;
    
    @Bean
    public BlobServiceClient blobServiceClient() {
        try {
            if (accountName.isEmpty() || accountKey.isEmpty()) {
                throw new IllegalStateException("Azure Storage account name and key are required");
            }
            
            log.info("Initializing Azure Blob Service Client for account: {}", accountName);
            
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
            
            BlobServiceClient blobServiceClient;
            if (!endpoint.isEmpty()) {
                // Use custom endpoint if provided
                blobServiceClient = new BlobServiceClientBuilder()
                        .endpoint(endpoint)
                        .credential(credential)
                        .buildClient();
            } else {
                // Use default endpoint format
                String defaultEndpoint = String.format("https://%s.blob.core.windows.net", accountName);
                blobServiceClient = new BlobServiceClientBuilder()
                        .endpoint(defaultEndpoint)
                        .credential(credential)
                        .buildClient();
            }
            
            log.info("✅ Azure Blob Service Client initialized successfully");
            return blobServiceClient;
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize Azure Blob Service Client", e);
            throw new RuntimeException("Failed to initialize Azure Blob Storage", e);
        }
    }
}