package com.carpool.dto.didit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for listing Didit reviews in the admin queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditReviewListItemDTO {
    private UUID id;
    private String sessionId;
    private UUID userId;
    private String userName;
    private String userMobile;
    private String verificationType;
    private String currentStatus; // PENDING, UNDER_REVIEW, APPROVED, DECLINED, RESUBMISSION_REQUESTED
    private String approvalStatus; // PENDING, APPROVED, DECLINED
    private String documentCountry;
    private String documentType;
    private String amlRiskLevel;
    private LocalDateTime createdAt;
    private LocalDateTime underReviewAt;
    private LocalDateTime approvedAt;
    private Integer reviewTimeMinutes; // Time spent in review
    private String approvedByAdminName;
    private boolean hasWarnings;
    private Integer warningCount;
}
