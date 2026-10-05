package com.carpool.dto.didit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to approve a Didit verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveReviewRequest {
    @NotNull(message = "Verification ID is required")
    private UUID verificationId;
    
    @NotBlank(message = "Comment is required")
    private String comment;
}
