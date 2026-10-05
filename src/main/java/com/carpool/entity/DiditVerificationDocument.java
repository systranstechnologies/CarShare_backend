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
 * Stores document images and extracted data from Didit verifications.
 * Each verification can have multiple documents (front, back, selfie).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "didit_verification_documents")
public class DiditVerificationDocument extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "didit_verification_id", nullable = false)
    private DiditVerification diditVerification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentSide documentSide; // FRONT, BACK, SELFIE

    @Column(length = 500)
    private String imageUrl; // Publicly accessible URL

    @Column(length = 255)
    private String imageDataKey; // S3 or storage key for secure access with signed URLs

    @Column(columnDefinition = "LONGTEXT")
    private String processedData; // JSON string of OCR processed data for this specific image

    @Column
    private Integer documentWidth;

    @Column
    private Integer documentHeight;

    @Column(columnDefinition = "TIMESTAMP NULL")
    private LocalDateTime uploadedAt;

    public DiditVerificationDocument(DiditVerification diditVerification, DocumentSide side) {
        this.diditVerification = diditVerification;
        this.documentSide = side;
    }

    public enum DocumentSide {
        FRONT,
        BACK,
        SELFIE
    }
}
