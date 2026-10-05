package com.carpool.dto.rewards;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateRedemptionRequest {
    @NotBlank private String accountHolderName;
    @NotBlank private String bankName;
    @NotBlank @Pattern(regexp = "[0-9]{6,20}", message = "Account number must contain 6 to 20 digits")
    private String accountNumber;
    @NotBlank private String confirmAccountNumber;
    @NotBlank @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "Invalid IFSC code")
    private String ifscCode;
    @Min(1) private long redeemAmount;
}
