package com.vaultguardian.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
public class AzureConfig {
    
    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
    public BlobServiceClient blobServiceClient(Environment env) {
        
        String accountName = env.getProperty("azure.storage.account-name", "");
        String accountKey = env.getProperty("azure.storage.account-key", "");
        
        log.info("🔵 Creating Azure Blob Service Client...");
        log.info("Account Name: '{}'", accountName);
        log.info("Account Key Present: {}", !accountKey.isEmpty());
        
        if (accountName.isEmpty() || accountKey.isEmpty()) {
            throw new IllegalStateException("Azure credentials missing. Check AZURE_STORAGE_ACCOUNT_NAME and AZURE_STORAGE_ACCOUNT_KEY");
        }
        
        try {
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
            String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
            
            BlobServiceClient client = new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .buildClient();
            
            log.info("✅ Azure Blob Service Client created successfully");
            return client;
            
        } catch (Exception e) {
            log.error("❌ Failed to create Azure client: {}", e.getMessage());
            throw new RuntimeException("Azure initialization failed", e);
        }
    }
}