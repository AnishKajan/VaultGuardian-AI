package com.vaultguardian.service;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {
    
    private final S3Client s3Client;
    private final S3Template s3Template;
    
    // FIXED: Match your actual bucket name
    @Value("${aws.s3.bucket.name:vaultguardian-ai}")
    private String bucketName;
    
    @Value("${aws.s3.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    public String uploadFile(MultipartFile file, String filename) {
        try {
            log.info("Uploading file to S3 bucket: {}, filename: {}", bucketName, filename);
            
            String key = generateS3Key(filename);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("original-filename", file.getOriginalFilename());
            metadata.put("content-type", file.getContentType());
            metadata.put("upload-timestamp", LocalDateTime.now().toString());
            metadata.put("file-size", String.valueOf(file.getSize()));
            
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .metadata(metadata);
            
            // Enable server-side encryption
            if (encryptionEnabled) {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }
            
            PutObjectRequest request = requestBuilder.build();
            
            s3Client.putObject(request, 
                software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));
            
            log.info("File uploaded successfully to S3: {}", key);
            return key;
            
        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        } catch (Exception e) {
            log.error("AWS S3 Error uploading file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }
    
    public byte[] downloadFile(String s3Key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            return response.readAllBytes();
            
        } catch (Exception e) {
            log.error("Error downloading file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
    
    public void deleteFile(String s3Key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.deleteObject(request);
            log.info("File deleted successfully from S3: {}", s3Key);
            
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }
    
    public boolean fileExists(String s3Key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.headObject(request);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence in S3: {}", s3Key, e);
            return false;
        }
    }
    
    public String getFileUrl(String s3Key, int expirationHours) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            // Generate pre-signed URL for secure access
            return s3Template.createSignedGetURL(bucketName, s3Key, 
                java.time.Duration.ofHours(expirationHours)).toString();
            
        } catch (Exception e) {
            log.error("Error generating signed URL for S3 file: {}", s3Key, e);
            throw new RuntimeException("Failed to generate file URL", e);
        }
    }
    
    private String generateS3Key(String filename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("documents/%s/%s", timestamp, filename);
    }
    
    public void initializeBucket() {
        try {
            log.info("Initializing S3 bucket: {}", bucketName);
            
            // Check if bucket exists
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            try {
                s3Client.headBucket(headBucketRequest);
                log.info("✅ S3 bucket '{}' already exists and is accessible", bucketName);
            } catch (NoSuchBucketException e) {
                log.error("❌ S3 bucket '{}' does not exist. Please create it in AWS console.", bucketName);
                throw new RuntimeException("S3 bucket does not exist: " + bucketName);
            } catch (Exception e) {
                log.error("❌ Error accessing S3 bucket '{}': {}", bucketName, e.getMessage());
                throw new RuntimeException("Failed to access S3 bucket: " + bucketName, e);
            }
            
        } catch (Exception e) {
            log.error("Error initializing S3 bucket", e);
            throw new RuntimeException("Failed to initialize S3 bucket", e);
        }
    }
    
    // Removed automatic bucket creation and policy setting since bucket exists
    // These operations require additional permissions
    
    public String getBucketName() {
        return bucketName;
    }
}