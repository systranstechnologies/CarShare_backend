package com.carpool.service;

import com.carpool.dto.owner.OwnerCreateRequest;
import com.carpool.dto.owner.OwnerResponse;
import com.carpool.entity.KycDocument;
import com.carpool.entity.KycDocumentType;
import com.carpool.entity.OwnerProfile;
import com.carpool.entity.Role;
import com.carpool.entity.VerificationStatus;
import com.carpool.exception.AppException;
import com.carpool.mapper.OwnerMapper;
import com.carpool.repository.KycDocumentRepository;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.repository.UserRepository;
import com.carpool.security.AuthFacade;
import com.carpool.security.AppUserPrincipal;
import com.carpool.validation.MobileNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final OwnerProfileRepository ownerRepository;
    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final MobileNormalizer mobileNormalizer;
    private final AuthFacade authFacade;
    private final OwnerMapper ownerMapper;
    private final NotificationService notificationService;
    private final Environment env;

    @Transactional
    public OwnerResponse create(OwnerCreateRequest request) {
        AppUserPrincipal principal = authFacade.currentUser();
        if (principal.getRole() != Role.OWNER && principal.getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only owner/admin can create owner profile");
        }
        var existing = ownerRepository.findByUserId(principal.getUserId());
        if (existing.isPresent()) {
            OwnerProfile owner = existing.get();
            if (principal.getRole() == Role.OWNER && owner.getUser().getVerificationStatus() != null) {
                VerificationStatus status = owner.getUser().getVerificationStatus().canonical();
                owner.setVerificationStatus(status);
                owner.setVerified(status.isApproved());
                owner = ownerRepository.save(owner);
            }
            return ownerMapper.toResponse(owner);
        }

        var user = userRepository.findById(principal.getUserId()).orElseThrow();
        OwnerProfile owner = new OwnerProfile();
        owner.setUser(user);
        owner.setName(request.getName());
        owner.setMobile(mobileNormalizer.normalize(request.getMobile()));
        owner.setPreferences(request.getPreferences());
        VerificationStatus verificationStatus = user.getVerificationStatus() == null
            ? VerificationStatus.NOT_STARTED
            : user.getVerificationStatus().canonical();
        owner.setVerificationStatus(verificationStatus);
        owner.setVerified(verificationStatus.isApproved());
        if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isBlank()) {
            owner.setProfilePhotoUrl(user.getProfilePhotoUrl());
        } else if (request.getProfilePhoto() != null && !request.getProfilePhoto().isEmpty()) {
            owner.setProfilePhotoUrl(fileStorageService.storePublicProfile(request.getProfilePhoto()));
        }
        log.info("Creating owner profile for user {} with mobile {} and profile photo {}", principal.getUserId(), owner.getMobile(), owner.getProfilePhotoUrl() != null);
        owner = ownerRepository.saveAndFlush(owner);

        if (request.getGovernmentIdProof() != null && !request.getGovernmentIdProof().isEmpty()) {
            KycDocument doc = new KycDocument();
            doc.setOwner(owner);
            doc.setType(KycDocumentType.GOVERNMENT_ID);
            doc.setPrivateFile(true);
            doc.setMimeType(request.getGovernmentIdProof().getContentType());
            doc.setSizeBytes(request.getGovernmentIdProof().getSize());
            doc.setStoragePath(fileStorageService.storePrivateKyc(request.getGovernmentIdProof()));
            kycDocumentRepository.save(doc);
        }
        return ownerMapper.toResponse(owner);
    }

    @Transactional(readOnly = true)
    public OwnerResponse current() {
        AppUserPrincipal principal = authFacade.currentUser();
        return ownerMapper.toResponse(ownerRepository.findByUserId(principal.getUserId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND", "Owner profile not found")));
    }

    public List<OwnerResponse> list() {
        return ownerRepository.findAll().stream().map(ownerMapper::toResponse).toList();
    }

    public OwnerResponse get(UUID ownerId) {
        return ownerMapper.toResponse(ownerRepository.findById(ownerId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Owner not found")));
    }
}
