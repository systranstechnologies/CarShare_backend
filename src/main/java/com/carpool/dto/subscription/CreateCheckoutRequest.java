package com.carpool.dto.subscription;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Data
public class CreateCheckoutRequest {
    @NotNull(message = "successUrl is required")
    @NotBlank(message = "successUrl cannot be blank")
    private String successUrl;
    
    @NotNull(message = "cancelUrl is required")
    @NotBlank(message = "cancelUrl cannot be blank")
    private String cancelUrl;
    
    @NotNull(message = "planId is required")
    @NotBlank(message = "planId cannot be blank")
    private String planId;

    private boolean useCoins;
}
