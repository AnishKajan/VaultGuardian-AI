package com.vaultguardian.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
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
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = false)
public class S3Service implements StorageService {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket.name:vaultguardian-ai}")
    private String bucketName;
    
    @Value("${aws.s3.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Override
    public String uploadFile(byte[] fileContent, String fileName, String contentType) {
        try {
            log.info("Uploading file to S3 bucket: {}, filename: {}", bucketName, fileName);
            
            // First check if bucket exists and is accessible
            try {
                HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.headBucket(headBucketRequest);
                log.debug("S3 bucket {} is accessible", bucketName);
            } catch (NoSuchBucketException e) {
                log.error("S3 bucket does not exist: {}", bucketName);
                throw new RuntimeException("S3 bucket not found: " + bucketName);
            } catch (S3Exception e) {
                log.error("Cannot access S3 bucket {}: {}", bucketName, e.awsErrorDetails().errorMessage());
                throw new RuntimeException("Cannot access S3 bucket: " + e.awsErrorDetails().errorMessage());
            }
            
            String key = generateS3Key(fileName);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("original-filename", fileName);
            metadata.put("content-type", contentType);
            metadata.put("upload-timestamp", LocalDateTime.now().toString());
            metadata.put("file-size", String.valueOf(fileContent.length));
            
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .metadata(metadata);
            
            if (encryptionEnabled) {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }
            
            PutObjectRequest request = requestBuilder.build();
            
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(fileContent));
            
            log.info("File uploaded successfully to S3: {}, ETag: {}", key, response.eTag());
            return key;
            
        } catch (S3Exception e) {
            log.error("AWS S3 Error: {} - {}", e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to upload to S3: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
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
    public byte[] downloadFile(String s3Key) {
        try {
            log.debug("Downloading file from S3: {}", s3Key);
            
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            byte[] content = response.readAllBytes();
            
            log.debug("Downloaded {} bytes from S3", content.length);
            return content;
            
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: {}", s3Key);
            throw new RuntimeException("File not found: " + s3Key);
        } catch (Exception e) {
            log.error("Error downloading file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
    
    @Override
    public void deleteFile(String s3Key) {
        try {
            log.debug("Deleting file from S3: {}", s3Key);
            
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
    
    @Override
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
    
    @Override
    public String getFileUrl(String s3Key) {
        // For security, we don't generate public URLs
        // Files should be accessed through the application's download endpoint
        return String.format("/api/documents/download/%s", s3Key);
    }
    
    private String generateS3Key(String filename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueId = String.valueOf(System.currentTimeMillis());
        return String.format("documents/%s/%s_%s", timestamp, uniqueId, filename);
    }
    
    public String getBucketName() {
        return bucketName;
    }
    
    // Initialize bucket on startup
    public void initializeBucket() {
        try {
            log.info("Checking S3 bucket: {}", bucketName);
            
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            try {
                s3Client.headBucket(headBucketRequest);
                log.info("✅ S3 bucket '{}' exists and is accessible", bucketName);
            } catch (NoSuchBucketException e) {
                log.error("❌ S3 bucket '{}' does not exist. Please create it in AWS console.", bucketName);
                throw new RuntimeException("S3 bucket does not exist: " + bucketName);
            } catch (S3Exception e) {
                log.error("❌ Error accessing S3 bucket '{}': {}", bucketName, e.awsErrorDetails().errorMessage());
                throw new RuntimeException("Failed to access S3 bucket: " + bucketName, e);
            }
            
        } catch (Exception e) {
            log.error("Error initializing S3 bucket", e);
            throw new RuntimeException("Failed to initialize S3 bucket", e);
        }
    }
}