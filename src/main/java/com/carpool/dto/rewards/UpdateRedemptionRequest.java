package com.carpool.dto.rewards;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRedemptionRequest {
    @NotBlank private String status;
    private String note;
    private String paymentReference;
}
