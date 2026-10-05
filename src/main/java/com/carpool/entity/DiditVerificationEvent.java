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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks events in the Didit verification lifecycle.
 * Creates an audit trail of what happened and when during the verification process.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "didit_verification_events")
public class DiditVerificationEvent extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "didit_verification_id", nullable = false)
    private DiditVerification diditVerification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType eventType;

    @Column(columnDefinition = "LONGTEXT")
    private String eventData; // JSON with event-specific details

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime eventTimestamp;

    public DiditVerificationEvent(DiditVerification diditVerification, EventType eventType, String eventData) {
        this.diditVerification = diditVerification;
        this.eventType = eventType;
        this.eventData = eventData;
        this.eventTimestamp = LocalDateTime.now();
    }

    public enum EventType {
        VERIFICATION_CREATED,
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        DECLINED,
        RESUBMITTED,
        EXPIRED,
        WEBHOOK_RECEIVED,
        ADMIN_VIEWED,
        ADMIN_APPROVED,
        ADMIN_DECLINED,
        RESUBMISSION_REQUESTED,
        ERROR
    }
}
