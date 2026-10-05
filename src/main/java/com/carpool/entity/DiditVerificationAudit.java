package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "didit_verification_audit", indexes = {
    @Index(name = "idx_didit_audit_user", columnList = "user_id"),
    @Index(name = "idx_didit_audit_role_status", columnList = "user_role,status"),
    @Index(name = "idx_didit_audit_session", columnList = "session_id")
})
public class DiditVerificationAudit extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;
    @Column(name = "user_id", nullable = false, columnDefinition = "char(36)")
    private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "user_role", nullable = false, length = 20)
    private Role userRole;
    @Column(name = "session_id", nullable = false, length = 120)
    private String sessionId;
    @Column(name = "workflow_id", length = 120)
    private String workflowId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private VerificationStatus status;
    @Column(length = 1000)
    private String decisionReason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_payload_json", nullable = false, columnDefinition = "json")
    private String rawPayloadJson;
}
