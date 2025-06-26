package com.vaultguardian.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "storage.provider", havingValue = "supabase", matchIfMissing = true)
public class SupabaseStorageService implements StorageService {
    
    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.key}")
    private String supabaseKey;
    
    @Value("${supabase.storage.bucket:documents}")
    private String bucketName;
    
    private final WebClient webClient;
    
    public SupabaseStorageService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    @Override
    public String uploadFile(byte[] fileContent, String fileName, String contentType) {
        try {
            // Generate unique storage key
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String uniqueId = UUID.randomUUID().toString();
            String storageKey = String.format("%s/%s-%s", timestamp, uniqueId, fileName);
            
            log.info("📤 Uploading file to Supabase Storage: {}", storageKey);
            
            // Prepare multipart form data
            MultiValueMap<String, Object> bodyBuilder = new LinkedMultiValueMap<>();
            bodyBuilder.add("file", new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });
            
            // Upload to Supabase Storage
            String response = webClient.post()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.info("✅ File uploaded successfully to Supabase: {}", storageKey);
            return storageKey;
            
        } catch (WebClientResponseException e) {
            log.error("❌ Failed to upload file to Supabase: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to upload file to Supabase Storage: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error uploading file to Supabase", e);
            throw new RuntimeException("Failed to upload file to Supabase Storage", e);
        }
    }
    
    // MultipartFile overload for compatibility
    public String uploadFile(MultipartFile file, String filename) {
        try {
            return uploadFile(file.getBytes(), filename, file.getContentType());
        } catch (IOException e) {
            log.error("Error reading file content", e);
            throw new RuntimeException("Failed to read file content", e);
        }
    }
    
    @Override
    public byte[] downloadFile(String storageKey) {
        try {
            log.info("📥 Downloading file from Supabase Storage: {}", storageKey);
            
            byte[] fileContent = webClient.get()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            
            log.info("✅ File downloaded successfully from Supabase: {} bytes", fileContent.length);
            return fileContent;
            
        } catch (WebClientResponseException e) {
            log.error("❌ Failed to download file from Supabase: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to download file from Supabase Storage: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error downloading file from Supabase", e);
            throw new RuntimeException("Failed to download file from Supabase Storage", e);
        }
    }
    
    @Override
    public void deleteFile(String storageKey) {
        try {
            log.info("🗑️ Deleting file from Supabase Storage: {}", storageKey);
            
            webClient.delete()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.info("✅ File deleted successfully from Supabase: {}", storageKey);
            
        } catch (WebClientResponseException e) {
            log.error("❌ Failed to delete file from Supabase: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to delete file from Supabase Storage: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error deleting file from Supabase", e);
            throw new RuntimeException("Failed to delete file from Supabase Storage", e);
        }
    }
    
    @Override
    public boolean fileExists(String storageKey) {
        try {
            webClient.head()
                    .uri(supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            
            return true;
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.error("❌ Error checking file existence in Supabase: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ Error checking file existence in Supabase", e);
            return false;
        }
    }
    
    @Override
    public String getFileUrl(String storageKey) {
        try {
            // Get signed URL for temporary access
            String signedUrlResponse = webClient.post()
                    .uri(supabaseUrl + "/storage/v1/object/sign/" + bucketName + "/" + storageKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"expiresIn\": 3600}") // 1 hour expiration
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // Parse the signed URL from response
            return supabaseUrl + "/storage/v1/object/sign/" + bucketName + "/" + storageKey;
            
        } catch (Exception e) {
            log.error("❌ Error getting file URL from Supabase", e);
            return null;
        }
    }
    
    public String getBucketName() {
        return bucketName;
    }
}