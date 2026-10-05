package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.didit.AdminAuditView;
import com.carpool.entity.Role;
import com.carpool.entity.VerificationStatus;
import com.carpool.repository.DiditVerificationAuditRepository;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/verifications")
public class AdminAuditController {
    private final DiditVerificationAuditRepository repository;
    private final AuthFacade authFacade;
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) Role role, @RequestParam(required = false) String sessionId, @RequestParam(required = false) VerificationStatus status) {
        if (authFacade.currentUser().getRole() != Role.ADMIN) throw new com.carpool.exception.AppException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
        var seenSessions = new java.util.HashSet<String>();
        var audits = repository.findAllByOrderByUpdatedAtDesc().stream()
            .filter(a -> a.getSessionId() == null || seenSessions.add(a.getSessionId()))
            .filter(a -> role == null || a.getUserRole() == role)
            .filter(a -> status == null || a.getStatus() == status)
            .filter(a -> sessionId == null || sessionId.equals(a.getSessionId()))
            .map(a -> AdminAuditView.builder().id(a.getId()).userId(a.getUserId()).userRole(a.getUserRole()).sessionId(a.getSessionId()).workflowId(a.getWorkflowId()).status(a.getStatus()).decisionReason(a.getDecisionReason()).rawPayloadJson(a.getRawPayloadJson()).createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt()).build()).toList();
        return ApiResponse.of(audits);
    }

}
