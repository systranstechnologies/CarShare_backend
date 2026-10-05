package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks webhook events from Didit for idempotent processing.
 * Prevents duplicate processing of the same webhook and provides audit trail.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "didit_webhook_events")
public class DiditWebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WebhookEventType eventType;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String eventPayload; // Raw JSON payload from Didit

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime diditTimestamp; // Timestamp from Didit's webhook

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime webhookReceivedAt; // When we received the webhook

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column
    private Integer processingAttempts = 0;

    @Column(columnDefinition = "LONGTEXT")
    private String lastError; // Last error message if processing failed

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime processedAt; // When webhook was successfully processed

    @Column(length = 255, unique = true)
    private String idempotencyKey; // Unique key for idempotent processing

    public DiditWebhookEvent(String sessionId, WebhookEventType eventType, String eventPayload, String idempotencyKey) {
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.eventPayload = eventPayload;
        this.idempotencyKey = idempotencyKey;
        this.processingStatus = ProcessingStatus.PENDING;
    }

    public void markProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processingAttempts = (this.processingAttempts != null ? this.processingAttempts : 0) + 1;
    }

    public void markProcessed() {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.lastError = error;
    }

    public enum WebhookEventType {
        APPROVED,
        DECLINED,
        IN_REVIEW,
        RESUBMITTED,
        EXPIRED
    }

    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
