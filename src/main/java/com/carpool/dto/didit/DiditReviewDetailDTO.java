package com.carpool.dto.didit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for detailed view of a Didit verification for admin review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditReviewDetailDTO {
    private UUID id;
    private String sessionId;
    
    // User Information
    private UUID userId;
    private String userName;
    private String userMobile;
    private String userEmail;
    private String userRole;
    
    // Verification Details
    private String verificationType;
    private String currentStatus;
    private String approvalStatus;
    private String documentType;
    private String documentCountry;
    
    // OCR Data
    private Map<String, Object> ocrData;
    
    // Face & Liveness
    private BigDecimal faceMatchScore;
    private String livenessStatus;
    
    // Risk Information
    private String amlRiskLevel;
    private List<String> riskFlags;
    private List<String> verificationWarnings;
    
    // Documents
    private List<DiditDocumentDTO> documents;
    
    // Approval Information
    private UUID approvedByAdminId;
    private String approvedByAdminName;
    private String approvalComment;
    private LocalDateTime approvedAt;
    
    // Resubmission Info
    private String requestedResubmissionType;
    private String resubmissionReason;
    private LocalDateTime resubmissionRequestedAt;
    
    // Timeline
    private List<DiditEventDTO> events;
    private List<DiditAuditLogDTO> auditLogs;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime underReviewAt;
}
