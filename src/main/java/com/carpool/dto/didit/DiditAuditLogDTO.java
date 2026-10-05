package com.carpool.dto.didit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditAuditLogDTO {
    private UUID id;
    private String action; // VIEWED, APPROVED, DECLINED, RESUBMITTED, COMMENTED
    private String adminName;
    private String ipAddress;
    private Map<String, Object> actionDetail;
    private LocalDateTime createdAt;
}
