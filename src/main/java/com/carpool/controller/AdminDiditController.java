package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.didit.ApproveReviewRequest;
import com.carpool.dto.didit.DeclineReviewRequest;
import com.carpool.entity.User;
import com.carpool.entity.VerificationStatus;
import com.carpool.exception.AppException;
import com.carpool.entity.DiditVerificationAudit;
import com.carpool.repository.DiditVerificationAuditRepository;
import com.carpool.repository.DiditVerificationRepository;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.repository.UserRepository;
import com.carpool.security.AuthFacade;
import com.carpool.service.DiditReviewService;
import com.carpool.service.DiditIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/didit")
public class AdminDiditController {
    private final AuthFacade authFacade;
    private final UserRepository userRepository;
    private final DiditVerificationAuditRepository auditRepository;
    private final DiditVerificationRepository verificationRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final DiditReviewService diditReviewService;
    private final DiditIntegrationService diditIntegrationService;

    @PostMapping("/{sessionId}/approve")
    public ApiResponse<?> approve(@PathVariable String sessionId,
                                  @RequestBody(required = false) Map<String, String> body,
                                  HttpServletRequest request) {
        if (verificationRepository.findBySessionId(sessionId).isEmpty()) {
            return ApiResponse.of(updateLegacyAudit(sessionId, body, true));
        }
        return ApiResponse.of(diditReviewService.approveReview(
            new ApproveReviewRequest(resolveVerificationId(sessionId), comment(body, "Approved by admin review.")),
            adminUser(), request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @PostMapping("/{sessionId}/reject")
    public ApiResponse<?> reject(@PathVariable String sessionId,
                                 @RequestBody(required = false) Map<String, String> body,
                                 HttpServletRequest request) {
        if (verificationRepository.findBySessionId(sessionId).isEmpty()) {
            return ApiResponse.of(updateLegacyAudit(sessionId, body, false));
        }
        return ApiResponse.of(diditReviewService.declineReview(
            new DeclineReviewRequest(resolveVerificationId(sessionId), comment(body, "Rejected by admin review.")),
            adminUser(), request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    private java.util.UUID resolveVerificationId(String sessionId) {
        return diditReviewService.getReviewBySessionId(sessionId).getId();
    }

    private User adminUser() {
        return userRepository.findById(authFacade.currentUser().getUserId())
            .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Admin user not found"));
    }

    private String comment(Map<String, String> body, String fallback) {
        String value = body == null ? null : body.get("comment");
        if (value == null || value.isBlank()) value = body == null ? null : body.get("reason");
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private DiditVerificationAudit updateLegacyAudit(String sessionId, Map<String, String> body, boolean approved) {
        DiditVerificationAudit audit = auditRepository.findFirstBySessionIdOrderByUpdatedAtDesc(sessionId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "Didit verification not found"));
        if (audit.getStatus() == VerificationStatus.APPROVED || audit.getStatus() == VerificationStatus.REJECTED) {
            throw new AppException(HttpStatus.CONFLICT, "ALREADY_DECIDED",
                "This verification already has a final decision and cannot be changed");
        }
        String decisionComment = comment(body, approved ? "Approved by admin review." : "Rejected by admin review.");
        diditIntegrationService.updateSessionStatus(sessionId, approved ? "Approved" : "Declined", decisionComment);
        VerificationStatus status = approved ? VerificationStatus.APPROVED : VerificationStatus.REJECTED;
        audit.setStatus(status);
        audit.setDecisionReason(decisionComment);
        auditRepository.save(audit);

        userRepository.findById(audit.getUserId()).ifPresent(user -> {
            user.setVerificationStatus(status);
            userRepository.save(user);
            ownerProfileRepository.findByUserId(user.getId()).ifPresent(owner -> {
                owner.setVerificationStatus(status);
                owner.setVerified(approved);
                ownerProfileRepository.save(owner);
            });
        });
        return audit;
    }
}