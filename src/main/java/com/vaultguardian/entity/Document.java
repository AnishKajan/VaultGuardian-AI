package com.vaultguardian.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(nullable = false)
    private String originalFilename;
    
    @Column(nullable = false)
    private String contentType;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(nullable = false)
    private String s3Key;
    
    @Column(nullable = false)
    private String s3Bucket;
    
    @Column(nullable = false, length = 64)
    private String sha256Hash;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;
    
    @Column(columnDefinition = "TEXT")
    private String extractedText;
    
    @Column(columnDefinition = "TEXT")
    private String llmAnalysis;
    
    @Column(columnDefinition = "TEXT")
    private String riskSummary;
    
    @ElementCollection
    @CollectionTable(name = "document_flags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "flag")
    private List<String> detectedFlags;
    
    @ElementCollection
    @CollectionTable(name = "document_categories", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "category")
    private List<String> categories;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;
    
    @Column(name = "encryption_key_id")
    private String encryptionKeyId;
    
    @Column(name = "is_quarantined")
    private Boolean isQuarantined = false;
    
    @Column(name = "quarantine_reason")
    private String quarantineReason;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
    
    public enum DocumentStatus {
        UPLOADING,
        SCANNING,
        ANALYZING,
        APPROVED,
        REJECTED,
        QUARANTINED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}