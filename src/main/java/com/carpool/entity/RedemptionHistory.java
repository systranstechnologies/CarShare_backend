package com.carpool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "redemption_history")
public class RedemptionHistory {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "redemption_id", nullable = false)
    private RedemptionRequest redemption;
    @Column(length = 20) private String fromStatus;
    @Column(nullable = false, length = 20) private String toStatus;
    @Column(nullable = false, columnDefinition = "char(36)") private UUID changedBy;
    @Column(length = 500) private String note;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
}
