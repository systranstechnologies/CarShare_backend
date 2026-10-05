package com.carpool.service;

import com.carpool.dto.didit.*;
import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Didit verification reviews and approvals.
 * Handles admin review workflow, approval/decline, resubmission requests,
 * and audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DiditReviewService {

    private final DiditVerificationRepository diditVerificationRepository;
    private final DiditVerificationDocumentRepository documentRepository;
    private final DiditVerificationEventRepository eventRepository;
    private final DiditReviewAuditLogRepository auditLogRepository;
    private final DiditWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final DiditIntegrationService diditIntegrationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    // =======================
    // REVIEW QUEUE OPERATIONS
    // =======================

    /**
     * Get all pending reviews for admin dashboard
     */
    public Page<DiditReviewListItemDTO> getPendingReviews(Pageable pageable) {
        Page<DiditVerification> page = diditVerificationRepository.findPendingReviews(pageable);
        return page.map(this::toListItemDTO);
    }
    /**
     * Get all approved reviews
     */
    public Page<DiditReviewListItemDTO> getApprovedReviews(Pageable pageable) {
        Page<DiditVerification> page = diditVerificationRepository.findApprovedReviews(pageable);
        return page.map(this::toListItemDTO);
    }

    /**
     * Get all declined reviews
     */
    public Page<DiditReviewListItemDTO> getDeclinedReviews(Pageable pageable) {
        Page<DiditVerification> page = diditVerificationRepository.findDeclinedReviews(pageable);
        return page.map(this::toListItemDTO);
    }

    /**
     * Get resubmission requests
     */
    public Page<DiditReviewListItemDTO> getResubmissionRequests(Pageable pageable) {
        Page<DiditVerification> page = diditVerificationRepository.findResubmissions(pageable);
        return page.map(this::toListItemDTO);
    }

    /**
     * Get reviews filtered by country and status
     */
    public Page<DiditReviewListItemDTO> filterReviews(String country, String status, Pageable pageable) {
        try {
            DiditVerification.DiditReviewStatus reviewStatus = DiditVerification.DiditReviewStatus.valueOf(status);
            Page<DiditVerification> page = diditVerificationRepository
                .findByCurrentStatusAndDocumentCountry(reviewStatus, country, pageable);
            return page.map(this::toListItemDTO);
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Invalid review status: " + status);
        }
    }

    /**
     * Search reviews by session ID or user mobile
     */
    public List<DiditReviewListItemDTO> searchReviews(String query) {
        // Try finding by session ID
        Optional<DiditVerification> bySession = diditVerificationRepository.findBySessionId(query);
        if (bySession.isPresent()) {
            return List.of(toListItemDTO(bySession.get()));
        }

        // Try finding by user mobile
        Optional<User> user = userRepository.findByMobile(query);
        if (user.isPresent()) {
            Optional<DiditVerification> byUser = diditVerificationRepository.findByUserId(user.get().getId());
            return byUser.map(v -> List.of(toListItemDTO(v))).orElse(Collections.emptyList());
        }

        return Collections.emptyList();
    }

    // =======================
    // REVIEW DETAIL OPERATIONS
    // =======================

    /**
     * Get detailed review information for a single verification
     */
    public DiditReviewDetailDTO getReviewDetail(UUID verificationId) {
        DiditVerification verification = diditVerificationRepository.findById(verificationId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                "Didit verification not found"));

        return toDetailDTO(verification);
    }

    /**
     * Get review by session ID
     */
    public DiditReviewDetailDTO getReviewBySessionId(String sessionId) {
        DiditVerification verification = diditVerificationRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                "Didit verification not found"));

        return toDetailDTO(verification);
    }

    // =======================
    // APPROVAL OPERATIONS
    // =======================

    /**
     * Approve a Didit verification and call Didit API
     */
    public DiditReviewDetailDTO approveReview(ApproveReviewRequest request, User admin, String ipAddress, String userAgent) {
        DiditVerification verification = diditVerificationRepository.findById(request.getVerificationId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                "Didit verification not found"));
        requirePendingDecision(verification);

        // Call Didit API to update status
        callDiditUpdateStatus(verification.getSessionId(), "Approved", request.getComment());

        // Update local verification
        verification.approve(admin, request.getComment());
        diditVerificationRepository.save(verification);

        // Add event
        addEvent(verification, DiditVerificationEvent.EventType.ADMIN_APPROVED,
            Map.of("comment", request.getComment(), "admin", admin.getName()));

        // Add audit log
        addAuditLog(verification, admin, DiditReviewAuditLog.AuditAction.APPROVED,
            Map.of("comment", request.getComment()), ipAddress, userAgent);

        // Update user's verification status
        User user = verification.getUser();
        user.setVerificationStatus(VerificationStatus.APPROVED);
        userRepository.save(user);
        ownerProfileRepository.findByUserId(user.getId()).ifPresent(owner -> {
            owner.setVerificationStatus(VerificationStatus.APPROVED);
            owner.setVerified(true);
            ownerProfileRepository.save(owner);
        });

        // Send notification to user
        notifyUser(user, "verification_approved");

        log.info("Verification {} approved by {}", verification.getId(), admin.getName());
        return toDetailDTO(verification);
    }

    /**
     * Decline a Didit verification and call Didit API
     */
    public DiditReviewDetailDTO declineReview(DeclineReviewRequest request, User admin, String ipAddress, String userAgent) {
        DiditVerification verification = diditVerificationRepository.findById(request.getVerificationId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                "Didit verification not found"));
        requirePendingDecision(verification);

        // Call Didit API to update status
        callDiditUpdateStatus(verification.getSessionId(), "Declined", request.getReason());

        // Update local verification
        verification.decline(admin, request.getReason());
        diditVerificationRepository.save(verification);

        // Add event
        addEvent(verification, DiditVerificationEvent.EventType.ADMIN_DECLINED,
            Map.of("reason", request.getReason(), "admin", admin.getName()));

        // Add audit log
        addAuditLog(verification, admin, DiditReviewAuditLog.AuditAction.DECLINED,
            Map.of("reason", request.getReason()), ipAddress, userAgent);

        // Update user's verification status
        User user = verification.getUser();
        user.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(user);
        ownerProfileRepository.findByUserId(user.getId()).ifPresent(owner -> {
            owner.setVerificationStatus(VerificationStatus.REJECTED);
            owner.setVerified(false);
            ownerProfileRepository.save(owner);
        });

        // Send notification to user
        notifyUser(user, "verification_rejected");

        log.info("Verification {} declined by {}", verification.getId(), admin.getName());
        return toDetailDTO(verification);
    }

    // =======================
    // RESUBMISSION OPERATIONS
    // =======================

    /**
     * Request resubmission of specific document or data
     */
    public DiditReviewDetailDTO requestResubmission(RequestResubmissionRequest request, User admin, String ipAddress, String userAgent) {
        DiditVerification verification = diditVerificationRepository.findById(request.getVerificationId())
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                "Didit verification not found"));

        // Update Didit with resubmission request
        callDiditUpdateStatus(verification.getSessionId(), "Resubmission",
            "Resubmission requested: " + request.getResubmissionType());

        // Update verification
        verification.requestResubmission(request.getResubmissionType(), request.getReason());
        diditVerificationRepository.save(verification);

        // Add event
        addEvent(verification, DiditVerificationEvent.EventType.RESUBMISSION_REQUESTED,
            Map.of("type", request.getResubmissionType(), "reason", request.getReason(), "admin", admin.getName()));

        // Add audit log
        addAuditLog(verification, admin, DiditReviewAuditLog.AuditAction.RESUBMITTED,
            Map.of("resubmissionType", request.getResubmissionType(), "reason", request.getReason()),
            ipAddress, userAgent);

        // Send notification to user
        notifyUser(verification.getUser(), "resubmission_requested");

        log.info("Resubmission requested for verification {} by {}", verification.getId(), admin.getName());
        return toDetailDTO(verification);
    }

    // =======================
    // DIDIT INTEGRATION
    // =======================

    /**
     * Handle webhook callback from Didit and create/update verification records
     */
    @Transactional
    public void handleDiditWebhook(String sessionId, String eventType, String eventPayload, String idempotencyKey) {
        // Check for idempotency
        Optional<DiditWebhookEvent> existingEvent = webhookEventRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEvent.isPresent()) {
            log.warn("Webhook already processed with idempotency key: {}", idempotencyKey);
            return;
        }

        // Parse webhook event
        DiditWebhookEvent.WebhookEventType webhookType = parseWebhookEventType(eventType);
        DiditWebhookEvent webhookEvent = new DiditWebhookEvent(sessionId, webhookType, eventPayload, idempotencyKey);
        webhookEvent.markProcessing();
        webhookEventRepository.save(webhookEvent);

        try {
            // Get or create verification record
            DiditVerification verification = diditVerificationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                    "No verification found for session: " + sessionId));

            // Process webhook based on event type
            switch (webhookType) {
                case IN_REVIEW -> verification.markUnderReview();
                case APPROVED -> {
                    verification.setCurrentStatus(DiditVerification.DiditReviewStatus.APPROVED);
                    User user = verification.getUser();
                    user.setVerificationStatus(VerificationStatus.APPROVED);
                    userRepository.save(user);
                    notifyUser(user, "didit_approved");
                }
                case DECLINED -> {
                    verification.setCurrentStatus(DiditVerification.DiditReviewStatus.DECLINED);
                    User user = verification.getUser();
                    user.setVerificationStatus(VerificationStatus.REJECTED);
                    userRepository.save(user);
                    notifyUser(user, "didit_declined");
                }
                case RESUBMITTED -> verification.setCurrentStatus(DiditVerification.DiditReviewStatus.RESUBMISSION_REQUESTED);
                case EXPIRED -> log.warn("Verification expired: {}", sessionId);
            }

            diditVerificationRepository.save(verification);
            addEvent(verification, DiditVerificationEvent.EventType.WEBHOOK_RECEIVED,
                Map.of("webhookType", webhookType.name(), "payload", eventPayload));

            webhookEvent.markProcessed();
            webhookEventRepository.save(webhookEvent);

            log.info("Webhook processed successfully for session: {}", sessionId);
        } catch (Exception e) {
            webhookEvent.markFailed(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            log.error("Failed to process webhook for session: {}", sessionId, e);
        }
    }

    // =======================
    // AUDIT LOG OPERATIONS
    // =======================

    /**
     * Get audit logs for a verification
     */
    public Page<DiditAuditLogDTO> getAuditLogs(UUID verificationId, Pageable pageable) {
        Page<DiditReviewAuditLog> logs = auditLogRepository.findByDiditVerificationId(verificationId, pageable);
        return logs.map(this::toAuditLogDTO);
    }

    /**
     * Get audit logs by admin
     */
    public Page<DiditAuditLogDTO> getAuditLogsByAdmin(UUID adminId, Pageable pageable) {
        Page<DiditReviewAuditLog> logs = auditLogRepository.findByAdminId(adminId, pageable);
        return logs.map(this::toAuditLogDTO);
    }

    // =======================
    // STATISTICS & REPORTING
    // =======================

    /**
     * Get review statistics
     */
    public ReviewStatisticsDTO getStatistics() {
        return ReviewStatisticsDTO.builder()
            .pendingReviews(diditVerificationRepository.countByCurrentStatus(DiditVerification.DiditReviewStatus.UNDER_REVIEW))
            .approvedReviews(diditVerificationRepository.countByCurrentStatus(DiditVerification.DiditReviewStatus.APPROVED))
            .declinedReviews(diditVerificationRepository.countByCurrentStatus(DiditVerification.DiditReviewStatus.DECLINED))
            .resubmissionRequests(diditVerificationRepository.countByCurrentStatus(DiditVerification.DiditReviewStatus.RESUBMISSION_REQUESTED))
            .build();
    }

    // =======================
    // HELPER METHODS
    // =======================

    private void callDiditUpdateStatus(String sessionId, String newStatus, String comment) {
        log.info("Updating Didit session status: session={}, status={}", sessionId, newStatus);
        diditIntegrationService.updateSessionStatus(sessionId, newStatus, comment);
    }

    private void requirePendingDecision(DiditVerification verification) {
        if (verification.getCurrentStatus() == DiditVerification.DiditReviewStatus.APPROVED
            || verification.getCurrentStatus() == DiditVerification.DiditReviewStatus.DECLINED
            || verification.getApprovalStatus() == DiditVerification.DiditApprovalStatus.APPROVED
            || verification.getApprovalStatus() == DiditVerification.DiditApprovalStatus.DECLINED) {
            throw new AppException(HttpStatus.CONFLICT, "ALREADY_DECIDED",
                "This verification already has a final decision and cannot be changed");
        }
    }

    private void addEvent(DiditVerification verification, DiditVerificationEvent.EventType eventType, Map<String, Object> data) {
        try {
            String eventData = objectMapper.writeValueAsString(data);
            DiditVerificationEvent event = new DiditVerificationEvent(verification, eventType, eventData);
            eventRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event data", e);
        }
    }

    private void addAuditLog(DiditVerification verification, User admin, DiditReviewAuditLog.AuditAction action,
                            Map<String, Object> actionDetail, String ipAddress, String userAgent) {
        try {
            String detail = objectMapper.writeValueAsString(actionDetail);
            DiditReviewAuditLog log = new DiditReviewAuditLog(verification, admin, action, detail, ipAddress, userAgent);
            auditLogRepository.save(log);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log data", e);
        }
    }

    private void notifyUser(User user, String templateKey) {
        try {
            // Send notification to user
            String title = switch (templateKey) {
                case "verification_approved" -> "Identity Verification Approved";
                case "verification_rejected" -> "Identity Verification Rejected";
                case "didit_approved" -> "Didit Verification Approved";
                case "didit_declined" -> "Didit Verification Declined";
                case "resubmission_requested" -> "Document Resubmission Required";
                default -> "Verification Update";
            };
            String body = switch (templateKey) {
                case "verification_approved" -> "Your identity has been verified successfully.";
                case "verification_rejected" -> "Your identity verification was rejected. Please resubmit.";
                case "didit_approved" -> "Didit verification is now complete and approved.";
                case "didit_declined" -> "Didit verification was declined. Please try again.";
                case "resubmission_requested" -> "Please resubmit your documents for verification.";
                default -> "Your verification status has been updated.";
            };
            notificationService.create(user.getId(), NotificationType.KYC_VERIFICATION_RESULT, title, body);
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", user.getId(), e);
        }
    }

    private DiditWebhookEvent.WebhookEventType parseWebhookEventType(String eventType) {
        return switch (eventType.toUpperCase()) {
            case "APPROVED" -> DiditWebhookEvent.WebhookEventType.APPROVED;
            case "DECLINED" -> DiditWebhookEvent.WebhookEventType.DECLINED;
            case "IN_REVIEW", "UNDER_REVIEW" -> DiditWebhookEvent.WebhookEventType.IN_REVIEW;
            case "RESUBMITTED" -> DiditWebhookEvent.WebhookEventType.RESUBMITTED;
            case "EXPIRED" -> DiditWebhookEvent.WebhookEventType.EXPIRED;
            default -> throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_TYPE",
                "Unknown webhook event type: " + eventType);
        };
    }

    // =======================
    // DTO CONVERSIONS
    // =======================

    private DiditReviewListItemDTO toListItemDTO(DiditVerification verification) {
        LocalDateTime approvedAt = verification.getApprovedAt();
        LocalDateTime underReviewAt = verification.getUnderReviewAt();
        Integer reviewTimeMinutes = null;

        if (approvedAt != null && underReviewAt != null) {
            reviewTimeMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(underReviewAt, approvedAt);
        }

        boolean hasWarnings = verification.getVerificationWarnings() != null && !verification.getVerificationWarnings().isEmpty();
        int warningCount = 0;
        if (hasWarnings) {
            try {
                List<?> warnings = objectMapper.readValue(verification.getVerificationWarnings(), List.class);
                warningCount = warnings.size();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse warnings", e);
            }
        }

        return DiditReviewListItemDTO.builder()
            .id(verification.getId())
            .sessionId(verification.getSessionId())
            .userId(verification.getUser().getId())
            .userName(verification.getUser().getFullName())
            .userMobile(verification.getUser().getMobile())
            .verificationType(verification.getVerificationType())
            .currentStatus(verification.getCurrentStatus().name())
            .approvalStatus(verification.getApprovalStatus() != null ? verification.getApprovalStatus().name() : "PENDING")
            .documentCountry(verification.getDocumentCountry())
            .documentType(verification.getDocumentType())
            .amlRiskLevel(verification.getAmlRiskLevel())
            .createdAt(toLocalDateTime(verification.getCreatedAt()))
            .underReviewAt(verification.getUnderReviewAt())
            .approvedAt(verification.getApprovedAt())
            .reviewTimeMinutes(reviewTimeMinutes)
            .approvedByAdminName(verification.getApprovedByAdmin() != null ? verification.getApprovedByAdmin().getFullName() : null)
            .hasWarnings(hasWarnings)
            .warningCount(warningCount)
            .build();
    }

    private DiditReviewDetailDTO toDetailDTO(DiditVerification verification) {
        List<DiditDocumentDTO> documents = verification.getDocuments().stream()
            .map(this::toDocumentDTO)
            .collect(Collectors.toList());

        List<DiditEventDTO> events = verification.getEvents().stream()
            .map(this::toEventDTO)
            .collect(Collectors.toList());

        List<DiditAuditLogDTO> auditLogs = verification.getAuditLogs().stream()
            .map(this::toAuditLogDTO)
            .collect(Collectors.toList());

        Map<String, Object> ocrData = null;
        if (verification.getOcrData() != null) {
            try {
                ocrData = objectMapper.readValue(verification.getOcrData(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse OCR data", e);
            }
        }

        List<String> riskFlags = null;
        if (verification.getRiskFlags() != null) {
            try {
                riskFlags = objectMapper.readValue(verification.getRiskFlags(), List.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse risk flags", e);
            }
        }

        List<String> warnings = null;
        if (verification.getVerificationWarnings() != null) {
            try {
                warnings = objectMapper.readValue(verification.getVerificationWarnings(), List.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse warnings", e);
            }
        }

        return DiditReviewDetailDTO.builder()
            .id(verification.getId())
            .sessionId(verification.getSessionId())
            .userId(verification.getUser().getId())
            .userName(verification.getUser().getFullName())
            .userMobile(verification.getUser().getMobile())
            .userRole(verification.getUser().getRole().name())
            .verificationType(verification.getVerificationType())
            .currentStatus(verification.getCurrentStatus().name())
            .approvalStatus(verification.getApprovalStatus() != null ? verification.getApprovalStatus().name() : "PENDING")
            .documentType(verification.getDocumentType())
            .documentCountry(verification.getDocumentCountry())
            .ocrData(ocrData)
            .faceMatchScore(verification.getFaceMatchScore())
            .livenessStatus(verification.getLivenessStatus())
            .amlRiskLevel(verification.getAmlRiskLevel())
            .riskFlags(riskFlags)
            .verificationWarnings(warnings)
            .documents(documents)
            .approvedByAdminId(verification.getApprovedByAdmin() != null ? verification.getApprovedByAdmin().getId() : null)
            .approvedByAdminName(verification.getApprovedByAdmin() != null ? verification.getApprovedByAdmin().getFullName() : null)
            .approvalComment(verification.getApprovalComment())
            .approvedAt(verification.getApprovedAt())
            .requestedResubmissionType(verification.getRequestedResubmissionType())
            .resubmissionReason(verification.getResubmissionReason())
            .resubmissionRequestedAt(verification.getResubmissionRequestedAt())
            .events(events)
            .auditLogs(auditLogs)
            .createdAt(toLocalDateTime(verification.getCreatedAt()))
            .underReviewAt(verification.getUnderReviewAt())
            .build();
    }

    private DiditDocumentDTO toDocumentDTO(DiditVerificationDocument doc) {
        Map<String, Object> processedData = null;
        if (doc.getProcessedData() != null) {
            try {
                processedData = objectMapper.readValue(doc.getProcessedData(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse document processed data", e);
            }
        }

        return DiditDocumentDTO.builder()
            .id(doc.getId())
            .documentSide(doc.getDocumentSide().name())
            .imageUrl(doc.getImageUrl())
            .documentWidth(doc.getDocumentWidth())
            .documentHeight(doc.getDocumentHeight())
            .processedData(processedData)
            .uploadedAt(doc.getUploadedAt())
            .build();
    }

    private DiditEventDTO toEventDTO(DiditVerificationEvent event) {
        Map<String, Object> eventData = null;
        if (event.getEventData() != null) {
            try {
                eventData = objectMapper.readValue(event.getEventData(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse event data", e);
            }
        }

        return DiditEventDTO.builder()
            .id(event.getId())
            .eventType(event.getEventType().name())
            .eventData(eventData)
            .eventTimestamp(event.getEventTimestamp())
            .build();
    }

    private DiditAuditLogDTO toAuditLogDTO(DiditReviewAuditLog auditLog) {
        Map<String, Object> actionDetail = null;
        if (auditLog.getActionDetail() != null) {
            try {
                actionDetail = objectMapper.readValue(auditLog.getActionDetail(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse audit action detail", e);
            }
        }

        return DiditAuditLogDTO.builder()
            .id(auditLog.getId())
            .action(auditLog.getAction().name())
            .adminName(auditLog.getAdmin().getFullName())
            .ipAddress(auditLog.getIpAddress())
            .actionDetail(actionDetail)
            .createdAt(toLocalDateTime(auditLog.getCreatedAt()))
            .build();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));
    }
}
