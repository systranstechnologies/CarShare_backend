package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log for all manual review actions taken by admins on Didit verifications.
 * Tracks who did what, when, and from where for compliance and security.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "didit_review_audit_logs")
public class DiditReviewAuditLog extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "didit_verification_id", nullable = false)
    private DiditVerification diditVerification;

    @ManyToOne(optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(columnDefinition = "LONGTEXT")
    private String actionDetail; // JSON with action-specific data

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    public DiditReviewAuditLog(DiditVerification diditVerification, User admin, AuditAction action, String actionDetail, String ipAddress, String userAgent) {
        this.diditVerification = diditVerification;
        this.admin = admin;
        this.action = action;
        this.actionDetail = actionDetail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    public enum AuditAction {
        VIEWED,
        APPROVED,
        DECLINED,
        RESUBMITTED,
        COMMENTED,
        DOCUMENT_DOWNLOADED,
        STATUS_CHECKED
    }
}
