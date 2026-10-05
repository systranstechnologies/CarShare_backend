package com.carpool.dto.registration;

import com.carpool.entity.RegistrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegistrationRequest {
    @NotNull
    private RegistrationType userType;

    @NotBlank
    private String mobileNumber;

    @NotBlank
    private String fullName;

    @Past
    private LocalDate dateOfBirth;

    @NotBlank
    private String gender;

    private String diditSessionId;
    private String otpCode;
    private String firebaseUid;
    private String subscriptionPlanId;
    private String utrNumber;
    private String paymentScreenshotUrl;
    private String profilePhotoUrl;
}
