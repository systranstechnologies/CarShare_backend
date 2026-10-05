package com.carpool.dto.didit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to decline a Didit verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclineReviewRequest {
    @NotNull(message = "Verification ID is required")
    private UUID verificationId;
    
    @NotBlank(message = "Decline reason is required")
    private String reason;
}
