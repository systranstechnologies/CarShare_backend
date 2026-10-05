package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DiditVerification entity tracks Didit KYC verification data for manual admin review.
 * This entity stores session information, extracted data, documents, and review status.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "didit_verifications")
public class DiditVerification extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 120)
    private String sessionId;

    @Column(nullable = false, length = 50)
    private String verificationType; // KYC, LIVENESS, AML, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiditReviewStatus currentStatus = DiditReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DiditApprovalStatus approvalStatus; // For admin approval/decline

    // Document Information
    @Column(length = 50)
    private String documentType; // Passport, License, ID, etc.

    @Column(length = 10)
    private String documentCountry;

    // OCR and Face Match Data
    @Column(columnDefinition = "LONGTEXT")
    private String ocrData; // JSON string of extracted document data

    @Column(precision = 5, scale = 2)
    private BigDecimal faceMatchScore; // Confidence score 0-100

    @Column(length = 30)
    private String livenessStatus; // PASSED, FAILED, INCONCLUSIVE

    // Risk and Compliance
    @Column(length = 20)
    private String amlRiskLevel; // LOW, MEDIUM, HIGH

    @Column(columnDefinition = "LONGTEXT")
    private String riskFlags; // JSON array of risk signals

    @Column(columnDefinition = "LONGTEXT")
    private String verificationWarnings; // JSON array of warnings from Didit

    // Approval Details
    @ManyToOne
    @JoinColumn(name = "approved_by_admin_id")
    private User approvedByAdmin;

    @Column(columnDefinition = "LONGTEXT")
    private String approvalComment; // Reason for approval or decline

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime approvedAt;

    // Resubmission Info
    @Column(length = 50)
    private String requestedResubmissionType; // OCR, DOCUMENT, SELFIE, LIVENESS

    @Column(columnDefinition = "LONGTEXT")
    private String resubmissionReason;

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime resubmissionRequestedAt;

    // Relationships
    @OneToMany(mappedBy = "diditVerification", orphanRemoval = true)
    private List<DiditVerificationDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "diditVerification", orphanRemoval = true)
    private List<DiditVerificationEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "diditVerification", orphanRemoval = true)
    private List<DiditReviewAuditLog> auditLogs = new ArrayList<>();

    // Audit timestamps
    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime verificationSubmittedAt;

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime underReviewAt;

    public DiditVerification(User user, String sessionId, String verificationType) {
        this.user = user;
        this.sessionId = sessionId;
        this.verificationType = verificationType;
        this.currentStatus = DiditReviewStatus.PENDING;
    }

    /**
     * Mark verification as pending review
     */
    public void markUnderReview() {
        this.currentStatus = DiditReviewStatus.UNDER_REVIEW;
        this.underReviewAt = LocalDateTime.now();
    }

    /**
     * Approve verification
     */
    public void approve(User admin, String comment) {
        this.currentStatus = DiditReviewStatus.APPROVED;
        this.approvalStatus = DiditApprovalStatus.APPROVED;
        this.approvedByAdmin = admin;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Decline verification
     */
    public void decline(User admin, String comment) {
        this.currentStatus = DiditReviewStatus.DECLINED;
        this.approvalStatus = DiditApprovalStatus.DECLINED;
        this.approvedByAdmin = admin;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Request resubmission of specific document/data
     */
    public void requestResubmission(String resubmissionType, String reason) {
        this.currentStatus = DiditReviewStatus.RESUBMISSION_REQUESTED;
        this.requestedResubmissionType = resubmissionType;
        this.resubmissionReason = reason;
        this.resubmissionRequestedAt = LocalDateTime.now();
    }

    public enum DiditReviewStatus {
        PENDING,
        UNDER_REVIEW,
        APPROVED,
        DECLINED,
        RESUBMISSION_REQUESTED
    }

    public enum DiditApprovalStatus {
        PENDING,
        APPROVED,
        DECLINED
    }
}
