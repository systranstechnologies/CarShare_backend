package com.carpool.dto.didit;

import com.carpool.entity.Role;
import com.carpool.entity.VerificationStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class AdminAuditView {
    private UUID id;
    private UUID userId;
    private Role userRole;
    private String sessionId;
    private String workflowId;
    private VerificationStatus status;
    private String decisionReason;
    private String rawPayloadJson;
    private Instant createdAt;
    private Instant updatedAt;
}
