package com.carpool.service;

import com.carpool.config.DiditProperties;
import com.carpool.dto.didit.DiditSessionResponse;
import com.carpool.entity.DiditSession;
import com.carpool.entity.Role;
import com.carpool.entity.User;
import com.carpool.entity.VerificationStatus;
import com.carpool.exception.AppException;
import com.carpool.repository.DiditSessionRepository;
import com.carpool.repository.UserRepository;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.security.AuthFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.HashMap;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.carpool.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiditIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(DiditIntegrationService.class);
    private final DiditProperties properties;
    private final DiditSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final AppProperties appProperties;
    private final AuthFacade authFacade;
    private final ObjectMapper objectMapper;
    private final DiditWebhookService webhookService;

    @PostConstruct
    void logConfiguration() {
        log.info("Didit integration v2 loaded: baseUrl={}, workflowConfigured={}, apiKeyConfigured={}",
            properties.getBaseUrl(), properties.getWorkflowId() != null && !properties.getWorkflowId().isBlank(),
            properties.getApiKey() != null && !properties.getApiKey().isBlank());
    }

    public DiditSessionResponse createSession(Role requestedRole, boolean nativeApp) {
        var principal = authFacade.currentUser();
        User user = userRepository.findById(principal.getUserId()).orElseThrow(() ->
            new AppException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user no longer exists"));
        boolean isOwner = user.getRole() == Role.OWNER || ownerProfileRepository.findByUserId(user.getId()).isPresent();
        boolean allowed = requestedRole == Role.OWNER ? isOwner : requestedRole == Role.PASSENGER && user.getRole() != Role.ADMIN;
        if (!allowed) {
            throw new AppException(HttpStatus.FORBIDDEN, "ROLE_NOT_AVAILABLE", "The requested registration role is not available for this account");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank() || properties.getWorkflowId() == null || properties.getWorkflowId().isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "DIDIT_NOT_CONFIGURED", "Identity verification is not configured");
        }
        String callbackBaseUrl = nativeApp && properties.getNativeCallbackBaseUrl() != null && !properties.getNativeCallbackBaseUrl().isBlank()
            ? properties.getNativeCallbackBaseUrl() : properties.getCallbackBaseUrl();
        String callback = callbackBaseUrl + (requestedRole == Role.OWNER
            ? "/owner/verification-status?role=OWNER" : "/passenger/verification-status?role=PASSENGER");
        JsonNode response;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("workflow_id", properties.getWorkflowId());
            body.put("vendor_data", user.getId().toString());
            body.put("callback", callback);
            body.put("callback_method", "both");
            body.put("metadata", Map.of("user_id", user.getId().toString(), "role", requestedRole.name()));
            String profilePhotoUrl = user.getProfilePhotoUrl();
            if (requestedRole == Role.OWNER) {
                profilePhotoUrl = ownerProfileRepository.findByUserId(user.getId()).map(owner -> owner.getProfilePhotoUrl()).orElse(profilePhotoUrl);
            }
            addExpectedDetails(body, user, profilePhotoUrl);
            String payloadJson = objectMapper.writeValueAsString(body);
            log.info("Creating Didit session: user={}, role={}, workflowConfigured={}, payloadKeys={}", user.getId(), requestedRole,
                body.get("workflow_id") != null && !body.get("workflow_id").toString().isBlank(), body.keySet());
            response = RestClient.builder().baseUrl(properties.getBaseUrl()).build().post().uri("/v3/session/")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", properties.getApiKey())
                .body(payloadJson)
                .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.error("Didit session request rejected: status={}, body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_REQUEST_FAILED",
                "Didit rejected the session request (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (JsonProcessingException ex) {
            log.error("Unable to serialize Didit session payload for user {}", user.getId(), ex);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "DIDIT_PAYLOAD_ERROR", "Unable to prepare identity verification request", ex);
        } catch (RestClientException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_UNAVAILABLE", "Didit is temporarily unavailable", ex);
        }
        String sessionId = text(response, "session_id", "id");
        String url = text(response, "url", "verification_url");
        if (sessionId == null || url == null) throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_INVALID_RESPONSE", "Didit did not return a session URL");
        try {
            DiditSession session = sessionRepository.findBySessionId(sessionId).orElseGet(DiditSession::new);
            session.setSessionId(sessionId); session.setUserId(user.getId()); session.setUserRole(requestedRole); session.setWorkflowId(properties.getWorkflowId());
            sessionRepository.save(session);
            user.setDiditSessionId(sessionId); user.setVerificationStatus(VerificationStatus.INITIATED); userRepository.save(user);
        } catch (RuntimeException ex) {
            log.error("Unable to persist Didit session {} for user {}", sessionId, user.getId(), ex);
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "DIDIT_SESSION_PERSIST_FAILED", "Unable to save identity verification session", ex);
        }
        return DiditSessionResponse.builder().sessionId(sessionId).verificationUrl(url).userId(user.getId()).role(requestedRole.name()).status("INITIATED").build();
    }

    public String syncCurrentStatus() {
        var principal = authFacade.currentUser();
        User user = userRepository.findById(principal.getUserId()).orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user no longer exists"));
        if (user.getDiditSessionId() == null || user.getDiditSessionId().isBlank()) return user.getVerificationStatus().name();
        try {
            JsonNode decision = RestClient.builder().baseUrl(properties.getBaseUrl()).build().get()
                .uri("/v3/session/{sessionId}/decision/", user.getDiditSessionId())
                .header("x-api-key", properties.getApiKey()).retrieve().body(JsonNode.class);
            String status = text(decision, "status");
            if (status == null) return user.getVerificationStatus().name();
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("session_id", user.getDiditSessionId()); envelope.put("status", status);
            envelope.put("webhook_type", "status.updated"); envelope.put("workflow_id", properties.getWorkflowId());
            envelope.put("vendor_data", user.getId().toString()); envelope.put("decision", decision);
            webhookService.processTrustedPayload(objectMapper.writeValueAsString(envelope));
            return status;
        } catch (RestClientException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_STATUS_UNAVAILABLE", "Unable to refresh Didit status", ex);
        } catch (Exception ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_STATUS_INVALID", "Didit returned an invalid decision", ex);
        }
    }

    public void updateSessionStatus(String sessionId, String newStatus, String comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("new_status", newStatus);
        if (comment != null && !comment.isBlank()) payload.put("comment", comment.trim());
        try {
            RestClient.builder().baseUrl(properties.getBaseUrl()).build().patch()
                .uri("/v3/session/{sessionId}/update-status/", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", properties.getApiKey())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error("Didit status update rejected for session {}: status={}, body={}", sessionId,
                ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_STATUS_UPDATE_FAILED",
                "Didit rejected the status update (HTTP " + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "DIDIT_UNAVAILABLE",
                "Didit is temporarily unavailable", ex);
        }
    }

    private void addExpectedDetails(Map<String, Object> body, User user, String profilePhotoUrl) {
        Map<String, Object> expected = new HashMap<>();
        if (user.getName() != null && !user.getName().isBlank()) {
            String[] nameParts = user.getName().trim().split("\\s+", 2);
            expected.put("first_name", nameParts[0]);
            if (nameParts.length == 2) expected.put("last_name", nameParts[1]);
        }
        if (user.getDateOfBirth() != null) expected.put("date_of_birth", user.getDateOfBirth().toString());
        if (!expected.isEmpty()) body.put("expected_details", expected);

        if (profilePhotoUrl == null || profilePhotoUrl.isBlank()) return;
        try {
            Path base = Paths.get(appProperties.getFileStorage().getLocalRoot()).toAbsolutePath().normalize();
            Path photo = base.resolve(profilePhotoUrl).normalize();
            if (!photo.startsWith(base) || Files.size(photo) > 2 * 1024 * 1024) return;
            body.put("portrait_image", Base64.getEncoder().encodeToString(Files.readAllBytes(photo)));
        } catch (java.nio.file.NoSuchFileException ex) {
            log.info("Profile photo {} is not available for Didit user {}; continuing without portrait image", profilePhotoUrl, user.getId());
        } catch (Exception ex) {
            log.warn("Unable to attach profile photo to Didit session for user {}: {}", user.getId(), ex.getMessage());
        }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) if (node != null && node.hasNonNull(name)) return node.get(name).asText();
        return null;
    }
}
