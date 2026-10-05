package com.carpool.service;

import com.carpool.config.DiditProperties;
import com.carpool.entity.*;
import com.carpool.exception.AppException;
import com.carpool.repository.DiditSessionRepository;
import com.carpool.repository.DiditVerificationAuditRepository;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class DiditWebhookService {
    private final DiditProperties properties;
    private final DiditSessionRepository sessionRepository;
    private final DiditVerificationAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Transactional
    public void process(String signatureV2, String signature, String signatureSimple, String timestamp, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            verifySignature(root, signatureV2, signature, signatureSimple, timestamp, payload);
            processPayload(root, payload);
        } catch (AppException e) { throw e; } catch (Exception e) { throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Unable to process Didit webhook"); }
    }

    @Transactional
    public void processTrustedPayload(String payload) {
        try {
            processPayload(objectMapper.readTree(payload), payload);
        } catch (AppException e) { throw e; } catch (Exception e) { throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_DIDIT_RESPONSE", "Unable to process Didit decision"); }
    }

    private void processPayload(JsonNode root, String payload) {
            String sessionId = text(root, "session_id", "sessionId");
            String rawStatus = text(root, "status", "decision");
            if (sessionId == null || rawStatus == null) throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "session_id and status are required");
            DiditSession session = sessionRepository.findBySessionId(sessionId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DIDIT_SESSION_NOT_FOUND", "Unknown Didit session"));
            VerificationStatus status = switch (rawStatus.toLowerCase()) {
                case "approved", "verified" -> VerificationStatus.APPROVED;
                case "declined", "rejected" -> VerificationStatus.REJECTED;
                case "initiated", "created" -> VerificationStatus.INITIATED;
                default -> VerificationStatus.UNDER_REVIEW;
            };
            var previous = auditRepository.findFirstBySessionId(sessionId);
            if (previous.isPresent() && previous.get().getStatus() == status) return;
            User user = userRepository.findById(session.getUserId()).orElseThrow();
            VerificationStatus previousStatus = user.getVerificationStatus() == null
                ? VerificationStatus.NOT_STARTED : user.getVerificationStatus().canonical();
            if (!isValidTransition(previousStatus, status)) {
                throw new AppException(HttpStatus.CONFLICT, "INVALID_VERIFICATION_TRANSITION",
                    "Invalid identity verification status transition");
            }
            user.setVerificationStatus(status); user.setKycVerified(status.isApproved()); userRepository.save(user);
            if (session.getUserRole() == Role.OWNER) ownerRepository.findByUserId(user.getId()).ifPresent(owner -> { owner.setVerificationStatus(status); owner.setVerified(status.isApproved()); ownerRepository.save(owner); });
            DiditVerificationAudit audit = new DiditVerificationAudit();
            audit.setUserId(user.getId()); audit.setUserRole(session.getUserRole()); audit.setSessionId(sessionId); audit.setWorkflowId(session.getWorkflowId()); audit.setStatus(status); audit.setDecisionReason(text(root, "decision_reason", "reason")); audit.setRawPayloadJson(payload);
            auditRepository.save(audit);
            String route = session.getUserRole() == Role.OWNER ? "/owner/verification-status?role=OWNER" : "/passenger/verification-status?role=PASSENGER";
            String title = switch (status) {
                case APPROVED, VERIFIED -> "Identity verification approved";
                case REJECTED -> "Identity verification declined";
                default -> "Identity verification under review";
            };
            String message = switch (status) {
                case APPROVED, VERIFIED -> "Your Didit identity verification was approved. You can continue with onboarding.";
                case REJECTED -> "Your Didit identity verification was declined. Open onboarding to verify again.";
                default -> "Your Didit identity verification is under review. We will notify you when a decision is available.";
            };
            notificationService.create(user.getId(), NotificationType.KYC_VERIFICATION_RESULT, title, message, route);
    }

    private boolean isValidTransition(VerificationStatus previous, VerificationStatus next) {
        if (previous == next) return true;
        return switch (previous) {
            case NOT_STARTED -> next == VerificationStatus.INITIATED;
            case INITIATED -> next == VerificationStatus.UNDER_REVIEW || next == VerificationStatus.APPROVED || next == VerificationStatus.REJECTED;
            case UNDER_REVIEW -> next == VerificationStatus.APPROVED || next == VerificationStatus.REJECTED;
            case REJECTED -> next == VerificationStatus.INITIATED;
            case APPROVED -> false;
            default -> isValidTransition(previous.canonical(), next);
        };
    }

    private void verifySignature(JsonNode root, String signatureV2, String signature, String signatureSimple, String timestamp, String payload) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) return;
        try {
            if (timestamp == null || Math.abs(java.time.Instant.now().getEpochSecond() - Long.parseLong(timestamp)) > 300) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Stale or missing Didit webhook timestamp");
            }
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expectedRaw = java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            boolean valid = signature != null && MessageDigest.isEqual(expectedRaw.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
            if (!valid && signatureSimple != null) {
                String simple = text(root, "timestamp") + ":" + text(root, "session_id") + ":" + text(root, "status") + ":" + text(root, "webhook_type");
                String expectedSimple = java.util.HexFormat.of().formatHex(mac.doFinal(simple.getBytes(StandardCharsets.UTF_8)));
                valid = MessageDigest.isEqual(expectedSimple.getBytes(StandardCharsets.UTF_8), signatureSimple.getBytes(StandardCharsets.UTF_8));
            }
            if (!valid && signatureV2 != null) {
                String canonical = objectMapper.writeValueAsString(sortKeys(root));
                String expectedV2 = java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
                valid = MessageDigest.isEqual(expectedV2.getBytes(StandardCharsets.UTF_8), signatureV2.getBytes(StandardCharsets.UTF_8));
            }
            if (!valid) throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Didit webhook signature");
        } catch (AppException e) { throw e; } catch (Exception e) { throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Didit webhook signature"); }
    }
    private JsonNode sortKeys(JsonNode node) {
        if (node == null || node.isValueNode()) return node;
        if (node.isArray()) { var array = objectMapper.createArrayNode(); node.forEach(value -> array.add(sortKeys(value))); return array; }
        var object = objectMapper.createObjectNode();
        java.util.List<String> names = new java.util.ArrayList<>(); node.fieldNames().forEachRemaining(names::add); java.util.Collections.sort(names);
        names.forEach(name -> object.set(name, sortKeys(node.get(name))));
        return object;
    }
    private String text(JsonNode node, String... names) { for (String name : names) if (node.hasNonNull(name)) return node.get(name).asText(); return null; }
}
