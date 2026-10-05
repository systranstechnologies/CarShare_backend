package com.carpool.service;

import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.UserRepository;
import com.carpool.security.AuthFacade;
import com.carpool.validation.MobileNormalizer;
import com.carpool.repository.OwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final AuthFacade authFacade;
    private final MobileNormalizer mobileNormalizer;
    private final FileStorageService fileStorageService;
    private final OwnerProfileRepository ownerProfileRepository;

    @Transactional
    public User upsertRegistrationProfile(com.carpool.dto.registration.RegistrationRequest request) {
        if (request == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "REGISTRATION_INVALID", "Registration request is required");
        }

        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        String normalizedMobile = mobileNormalizer.normalize(request.getMobileNumber());
        if (!normalizedMobile.equals(user.getMobile())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "MOBILE_MISMATCH", "The mobile number does not match the current user account");
        }

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Full name is required");
        }

        LocalDate dob = request.getDateOfBirth();
        if (dob == null || Period.between(dob, LocalDate.now()).getYears() < 18) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Users must be at least 18 years old");
        }

        if (request.getGender() == null || request.getGender().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Gender is required");
        }

        user.setRegistrationType(request.getUserType());
        user.setRegistrationStage(RegistrationStage.BASIC_DETAILS_COMPLETED);
        user.setFullName(request.getFullName().trim());
        user.setName(request.getFullName().trim());
        user.setDateOfBirth(dob);
        user.setAge(Period.between(dob, LocalDate.now()).getYears());
        user.setGender(request.getGender().trim());
        user.setMobileNumber(normalizedMobile);
        user.setMobileVerified(false);
        user.setUpdatedAt(java.time.Instant.now());

        return userRepository.save(user);
    }

    @Transactional
    public User markOtpVerified(String firebaseUid) {
        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Firebase UID is required");
        }

        user.setFirebaseUid(firebaseUid);
        user.setMobileVerified(true);
        user.setOtpVerified(true);
        user.setOtpVerifiedOn(LocalDateTime.now());
        user.setRegistrationStage(RegistrationStage.OTP_VERIFIED);
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User markDiditStatus(String status, String sessionId) {
        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        VerificationStatus normalizedStatus = VerificationStatus.valueOf(status.trim().toUpperCase());
        user.setDiditStatus(normalizedStatus);
        user.setDiditSessionId(sessionId);
        user.setDiditLastCheckedAt(LocalDateTime.now());
        user.setVerificationStatus(normalizedStatus);
        if (normalizedStatus == VerificationStatus.APPROVED) {
            user.setRegistrationStage(RegistrationStage.DOCUMENT_VERIFIED);
            user.setKycVerified(true);
        }
        if (normalizedStatus == VerificationStatus.REJECTED) {
            user.setKycVerified(false);
        }
        ownerProfileRepository.findByUserId(user.getId()).ifPresent(owner -> {
            owner.setVerificationStatus(normalizedStatus.canonical());
            owner.setVerified(normalizedStatus.isApproved());
            ownerProfileRepository.save(owner);
        });
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User markProfilePhoto(MultipartFile profilePhoto) {
        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (profilePhoto == null || profilePhoto.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Profile photo is required");
        }

        user.setProfilePhotoUrl(fileStorageService.storePublicProfile(profilePhoto));
        if (user.getRegistrationType() == RegistrationType.PASSENGER) {
            user.setRegistrationStage(RegistrationStage.REGISTRATION_COMPLETED);
            user.setRegistrationCompleted(true);
        } else {
            user.setRegistrationStage(RegistrationStage.PROFILE_PHOTO_COMPLETED);
        }
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User selectSubscriptionPlan(String planId) {
        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (planId == null || planId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Subscription plan is required");
        }

        user.setSubscriptionPlanId(planId);
        user.setRegistrationStage(RegistrationStage.SUBSCRIPTION_SELECTED);
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User submitPayment(String utrNumber, String screenshotUrl) {
        UUID userId = authFacade.currentUser().getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (utrNumber == null || !utrNumber.matches("^[A-Z0-9]{10,25}$")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid UTR number format");
        }

        boolean duplicate = userRepository.findAll().stream()
            .anyMatch(candidate -> candidate.getPaymentUTR() != null && candidate.getPaymentUTR().equalsIgnoreCase(utrNumber)
                && !Objects.equals(candidate.getId(), user.getId()));
        if (duplicate) {
            throw new AppException(HttpStatus.CONFLICT, "DUPLICATE_UTR", "UTR has already been used for another registration");
        }

        user.setPaymentUTR(utrNumber);
        user.setPaymentScreenshotUrl(screenshotUrl);
        user.setSubscriptionStatus(SubscriptionStatus.VERIFICATION_IN_PROGRESS);
        user.setRegistrationStage(RegistrationStage.PAYMENT_SUBMITTED);
        user.setRegistrationCompleted(true);
        user.setUpdatedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    public RegistrationStage currentStage(User user) {
        if (user == null) return RegistrationStage.USER_TYPE_SELECTED;
        if (user.isRegistrationCompleted()) return RegistrationStage.REGISTRATION_COMPLETED;
        return user.getRegistrationStage() == null ? RegistrationStage.USER_TYPE_SELECTED : user.getRegistrationStage();
    }
}
