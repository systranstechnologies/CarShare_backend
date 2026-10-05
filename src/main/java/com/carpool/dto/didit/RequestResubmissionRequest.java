package com.carpool.dto.didit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to ask for resubmission of a specific Didit verification component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestResubmissionRequest {
    @NotNull(message = "Verification ID is required")
    private UUID verificationId;
    
    @NotBlank(message = "Resubmission type is required (OCR, DOCUMENT, SELFIE, LIVENESS)")
    private String resubmissionType; // OCR, DOCUMENT, SELFIE, LIVENESS
    
    @NotBlank(message = "Reason is required")
    private String reason;
}
