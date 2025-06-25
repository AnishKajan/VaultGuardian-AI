package com.vaultguardian.controller;

import com.vaultguardian.dto.DocumentResponseDto;
import com.vaultguardian.dto.DashboardAnalyticsDto;
import com.vaultguardian.dto.DocumentSummaryDto;
import com.vaultguardian.entity.Document;
import com.vaultguardian.entity.User;
import com.vaultguardian.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
// REMOVED: @CrossOrigin annotation - let SecurityConfig handle CORS globally
public class DocumentController {
    
    private final DocumentService documentService;
    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        
        log.info("Upload request received from user: {}, file: {}", 
                currentUser.getUsername(), file.getOriginalFilename());
        
        try {
            // Validate file
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid File");
                error.put("message", "File is empty");
                error.put("errorCode", "INVALID_FILE_TYPE");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Check file size (50MB limit)
            if (file.getSize() > 50 * 1024 * 1024) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "File Too Large");
                error.put("message", "File size exceeds 50MB limit");
                error.put("errorCode", "FILE_TOO_LARGE");
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
            }
            
            Document document = documentService.uploadDocument(file, currentUser);
            DocumentResponseDto response = convertToResponseDto(document);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid upload request: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Upload Error");
            error.put("message", e.getMessage());
            error.put("errorCode", "UPLOAD_ERROR");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error uploading document", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Upload Failed");
            error.put("message", "Internal server error during upload");
            error.put("errorCode", "UPLOAD_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @GetMapping
    public ResponseEntity<List<DocumentSummaryDto>> getUserDocuments(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            List<Document> documents = documentService.getUserDocuments(currentUser);
            List<DocumentSummaryDto> response = documents.stream()
                    .map(this::convertToSummaryDto)
                    .toList();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving user documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDto> getDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        
        try {
            Document document = documentService.getDocumentById(id, currentUser);
            DocumentResponseDto response = convertToResponseDto(document);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error retrieving document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        
        try {
            byte[] fileContent = documentService.downloadDocument(id, currentUser);
            Document document = documentService.getDocumentById(id, currentUser);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + document.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(document.getContentType()))
                    .contentLength(fileContent.length)
                    .body(resource);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error downloading document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        
        try {
            documentService.deleteDocument(id, currentUser);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error deleting document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSummaryDto>> searchDocuments(
            @RequestParam String query,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {
        
        try {
            List<Document> documents = documentService.searchDocuments(query, currentUser, pageable);
            List<DocumentSummaryDto> response = documents.stream()
                    .map(this::convertToSummaryDto)
                    .toList();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error searching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<DashboardAnalyticsDto> getDashboardAnalytics(
            @AuthenticationPrincipal User currentUser) {
        
        try {
            DashboardAnalyticsDto analytics = documentService.getDashboardAnalytics(currentUser);
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            log.error("Error retrieving dashboard analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{id}/quarantine")
    public ResponseEntity<Void> quarantineDocument(
            @PathVariable Long id,
            @RequestParam String reason,
            @AuthenticationPrincipal User currentUser) {
        
        try {
            // Only security officers and admins can quarantine
            if (!hasSecurityRole(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            documentService.quarantineDocument(id, reason, currentUser);
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error quarantining document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private boolean hasSecurityRole(User user) {
        return user.getRoles().contains(User.Role.SECURITY_OFFICER) ||
               user.getRoles().contains(User.Role.ADMIN);
    }
    
    private DocumentResponseDto convertToResponseDto(Document document) {
        return DocumentResponseDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename()) // FIXED: Use originalFilename
                .filename(document.getOriginalFilename())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .riskLevel(document.getRiskLevel())
                .sha256Hash(document.getSha256Hash())
                .riskSummary(document.getRiskSummary())
                .detectedFlags(document.getDetectedFlags())
                .categories(document.getCategories())
                .isQuarantined(document.getIsQuarantined())
                .quarantineReason(document.getQuarantineReason())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .lastAccessedAt(document.getLastAccessedAt())
                .uploadedBy(document.getUploadedBy().getUsername())
                .build();
    }
    
    private DocumentSummaryDto convertToSummaryDto(Document document) {
        return DocumentSummaryDto.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename()) // FIXED: Use originalFilename
                .filename(document.getOriginalFilename())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .riskLevel(document.getRiskLevel())
                .flagCount(document.getDetectedFlags() != null ? document.getDetectedFlags().size() : 0)
                .isQuarantined(document.getIsQuarantined())
                .createdAt(document.getCreatedAt())
                .build();
    }
}