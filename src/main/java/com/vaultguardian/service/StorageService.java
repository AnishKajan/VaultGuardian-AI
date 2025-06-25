package com.vaultguardian.service;

/**
 * Storage service interface for file operations
 * Implementations can use AWS S3, Supabase Storage, or other providers
 */
public interface StorageService {
    
    /**
     * Upload file to storage
     * @param fileContent File content as byte array
     * @param fileName Original filename
     * @param contentType MIME type
     * @return Storage key/path for the uploaded file
     */
    String uploadFile(byte[] fileContent, String fileName, String contentType);
    
    /**
     * Download file from storage
     * @param storageKey Storage key/path
     * @return File content as byte array
     */
    byte[] downloadFile(String storageKey);
    
    /**
     * Delete file from storage
     * @param storageKey Storage key/path
     */
    void deleteFile(String storageKey);
    
    /**
     * Check if file exists in storage
     * @param storageKey Storage key/path
     * @return true if file exists
     */
    boolean fileExists(String storageKey);
    
    /**
     * Get file URL for direct access (if supported)
     * @param storageKey Storage key/path
     * @return Public URL or null if not supported
     */
    String getFileUrl(String storageKey);
}