package com.vaultguardian.repository;

import com.vaultguardian.entity.Document;
import com.vaultguardian.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * Check if a document with the given SHA256 hash already exists
     */
    boolean existsBySha256Hash(String sha256Hash);
    
    /**
     * Find all documents uploaded by a specific user, ordered by creation date descending
     */
    List<Document> findByUploadedByOrderByCreatedAtDesc(User uploadedBy);
    
    /**
     * Find documents by user with pagination
     */
    Page<Document> findByUploadedBy(User uploadedBy, Pageable pageable);
    
    /**
     * Find documents by status
     */
    List<Document> findByStatus(Document.DocumentStatus status);
    
    /**
     * Find documents by risk level
     */
    List<Document> findByRiskLevel(Document.RiskLevel riskLevel);
    
    /**
     * Find quarantined documents
     */
    List<Document> findByIsQuarantined(Boolean isQuarantined);
    
    /**
     * Search documents by filename (case-insensitive)
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Document> searchByFilename(@Param("query") String query);
    
    /**
     * Search documents by content (extracted text)
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Document> searchByContent(@Param("query") String query);
    
    /**
     * Search documents by filename or content for a specific user
     */
    @Query("SELECT d FROM Document d WHERE d.uploadedBy = :user AND " +
           "(LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Document> searchByUser(@Param("user") User user, @Param("query") String query, Pageable pageable);
    
    /**
     * Count documents by status
     */
    long countByStatus(Document.DocumentStatus status);
    
    /**
     * Count documents by risk level
     */
    long countByRiskLevel(Document.RiskLevel riskLevel);
    
    /**
     * Count quarantined documents
     */
    long countByIsQuarantined(Boolean isQuarantined);
    
    /**
     * Count documents uploaded today
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.createdAt >= :startOfDay")
    long countDocumentsUploadedToday(@Param("startOfDay") LocalDateTime startOfDay);
    
    /**
     * Count documents by user
     */
    long countByUploadedBy(User uploadedBy);
    
    /**
     * Find documents with high risk level
     */
    @Query("SELECT d FROM Document d WHERE d.riskLevel IN ('HIGH', 'CRITICAL')")
    List<Document> findHighRiskDocuments();
    
    /**
     * Count high risk documents
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.riskLevel IN ('HIGH', 'CRITICAL')")
    long countHighRiskDocuments();
    
    /**
     * Find recent documents (last 24 hours)
     */
    @Query("SELECT d FROM Document d WHERE d.createdAt >= :since ORDER BY d.createdAt DESC")
    List<Document> findRecentDocuments(@Param("since") LocalDateTime since);
    
    /**
     * Find documents that need processing (stuck in processing states)
     */
    @Query("SELECT d FROM Document d WHERE d.status IN ('UPLOADING', 'SCANNING', 'ANALYZING') " +
           "AND d.createdAt < :timeout")
    List<Document> findStuckDocuments(@Param("timeout") LocalDateTime timeout);
    
    /**
     * Find documents by categories
     */
    @Query("SELECT d FROM Document d JOIN d.categories c WHERE c = :category")
    List<Document> findByCategory(@Param("category") String category);
    
    /**
     * Get risk distribution statistics
     */
    @Query("SELECT d.riskLevel, COUNT(d) FROM Document d GROUP BY d.riskLevel")
    List<Object[]> getRiskDistribution();
    
    /**
     * Get category distribution statistics
     */
    @Query("SELECT c, COUNT(d) FROM Document d JOIN d.categories c GROUP BY c")
    List<Object[]> getCategoryDistribution();
}