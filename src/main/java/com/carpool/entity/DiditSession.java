package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter @Setter
@Entity @Table(name = "didit_sessions")
public class DiditSession extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;
    @Column(nullable = false, unique = true, length = 120)
    private String sessionId;
    @Column(nullable = false, columnDefinition = "char(36)")
    private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role userRole;
    @Column(length = 120)
    private String workflowId;
}
