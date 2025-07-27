package com.vaultguardian.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
public class AzureBlobService implements StorageService {
    
    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    
    // Constructor injection - no circular dependency
    public AzureBlobService(BlobServiceClient blobServiceClient,
                           @Value("${azure.storage.container-name:vaultguardian-ai}") String containerName) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
        log.info("🔵 AzureBlobService initialized with container: {}", containerName);
    }
    
    @Override
    public String uploadFile(byte[] fileContent, String fileName, String contentType) {
        try {
            log.info("Uploading file to Azure Blob Storage: container={}, filename={}", containerName, fileName);
            
            // Ensure container exists
            BlobContainerClient containerClient = ensureContainerExists();
            
            // Generate unique blob name
            String blobName = generateBlobName(fileName);
            
            // Get blob client
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            // Prepare metadata - Azure has strict rules for metadata values
            Map<String, String> metadata = new HashMap<>();
            
            // Sanitize filename for Azure metadata (remove special characters)
            String sanitizedFilename = fileName
                .replaceAll("[^a-zA-Z0-9._-]", "_")  // Replace invalid chars with underscore
                .replaceAll("_{2,}", "_")             // Replace multiple underscores with single
                .trim();
            
            // Azure metadata keys cannot have hyphens, use underscore instead
            metadata.put("originalfilename", sanitizedFilename); // No hyphens in key names
            metadata.put("contenttype", contentType != null ? 
                contentType.replaceAll("[^a-zA-Z0-9/.-]", "") : "application/octet-stream");
            metadata.put("uploadtimestamp", LocalDateTime.now().toString());
            metadata.put("filesize", String.valueOf(fileContent.length));
            metadata.put("application", "vaultguardian");
            
            // Upload file with metadata
            blobClient.upload(BinaryData.fromBytes(fileContent), true);
            
            // Set metadata
            blobClient.setMetadata(metadata);
            
            // Set content type
            if (contentType != null && !contentType.isEmpty()) {
                blobClient.setHttpHeaders(new com.azure.storage.blob.models.BlobHttpHeaders()
                        .setContentType(contentType));
            }
            
            log.info("File uploaded successfully to Azure Blob Storage: {}", blobName);
            return blobName;
            
        } catch (BlobStorageException e) {
            log.error("Azure Blob Storage Error: {} - {}", e.getErrorCode(), e.getMessage());
            throw new RuntimeException("Failed to upload to Azure Blob Storage: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error uploading file to Azure Blob Storage", e);
            throw new RuntimeException("Failed to upload file to Azure Blob Storage: " + e.getMessage(), e);
        }
    }
    
    // MultipartFile overload for convenience
    public String uploadFile(MultipartFile file, String filename) {
        try {
            return uploadFile(file.getBytes(), filename, file.getContentType());
        } catch (IOException e) {
            log.error("Error reading file content", e);
            throw new RuntimeException("Failed to read file content", e);
        }
    }
    
    @Override
    public byte[] downloadFile(String blobName) {
        try {
            log.debug("Downloading file from Azure Blob Storage: {}", blobName);
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            if (!blobClient.exists()) {
                log.error("Blob not found in Azure Storage: {}", blobName);
                throw new RuntimeException("File not found: " + blobName);
            }
            
            BinaryData binaryData = blobClient.downloadContent();
            byte[] content = binaryData.toBytes();
            
            log.debug("Downloaded {} bytes from Azure Blob Storage", content.length);
            return content;
            
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND) {
                log.error("Blob not found in Azure Storage: {}", blobName);
                throw new RuntimeException("File not found: " + blobName);
            }
            log.error("Azure Blob Storage error downloading file: {}", blobName, e);
            throw new RuntimeException("Failed to download file from Azure Blob Storage", e);
        } catch (Exception e) {
            log.error("Error downloading file from Azure Blob Storage: {}", blobName, e);
            throw new RuntimeException("Failed to download file from Azure Blob Storage", e);
        }
    }
    
    @Override
    public void deleteFile(String blobName) {
        try {
            log.debug("Deleting file from Azure Blob Storage: {}", blobName);
            
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            
            if (blobClient.exists()) {
                blobClient.delete();
                log.info("File deleted successfully from Azure Blob Storage: {}", blobName);
            } else {
                log.warn("Attempted to delete non-existent blob: {}", blobName);
            }
            
        } catch (Exception e) {
            log.error("Error deleting file from Azure Blob Storage: {}", blobName, e);
            throw new RuntimeException("Failed to delete file from Azure Blob Storage", e);
        }
    }
    
    @Override
    public boolean fileExists(String blobName) {
        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists();
            
        } catch (Exception e) {
            log.error("Error checking file existence in Azure Blob Storage: {}", blobName, e);
            return false;
        }
    }
    
    @Override
    public String getFileUrl(String blobName) {
        // For security, we don't generate public URLs
        // Files should be accessed through the application's download endpoint
        return String.format("/api/documents/download/%s", blobName);
    }
    
    private String generateBlobName(String filename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = String.valueOf(System.currentTimeMillis());
        return String.format("documents/%s/%s_%s", timestamp, uniqueId, filename);
    }
    
    private BlobContainerClient ensureContainerExists() {
        try {
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            
            if (!containerClient.exists()) {
                log.info("Creating Azure Blob Storage container: {}", containerName);
                containerClient.create();
                log.info("✅ Azure Blob Storage container created: {}", containerName);
            }
            
            return containerClient;
            
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.CONTAINER_ALREADY_EXISTS) {
                log.debug("Container already exists: {}", containerName);
                return blobServiceClient.getBlobContainerClient(containerName);
            }
            log.error("Failed to create/access container: {}", containerName, e);
            throw new RuntimeException("Failed to access Azure Blob Storage container", e);
        }
    }
    
    public String getContainerName() {
        return containerName;
    }
    
    // Initialize container on startup
    public void initializeContainer() {
        try {
            log.info("Checking Azure Blob Storage container: {}", containerName);
            
            BlobContainerClient containerClient = ensureContainerExists();
            
            // Verify access by listing a few blobs (limit to 1 for efficiency)
            try {
                containerClient.listBlobs().stream().limit(1).forEach(blob -> {
                    log.debug("Container access verified, found blob: {}", blob.getName());
                });
            } catch (Exception e) {
                log.warn("Could not list blobs (may be empty container): {}", e.getMessage());
            }
            
            log.info("✅ Azure Blob Storage container '{}' is accessible", containerName);
            
        } catch (Exception e) {
            log.error("❌ Error initializing Azure Blob Storage container", e);
            throw new RuntimeException("Failed to initialize Azure Blob Storage container", e);
        }
    }
}