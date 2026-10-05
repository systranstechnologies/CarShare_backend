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
@Table(name = "redemption_requests")
public class RedemptionRequest extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "wallet_id", nullable = false)
    private UserWallet wallet;
    @Column(nullable = false, length = 120) private String accountHolderName;
    @Column(nullable = false, length = 120) private String bankName;
    @Lob @Column(nullable = false) private String accountNumberEncrypted;
    @Column(nullable = false, length = 4) private String accountNumberLast4;
    @Lob @Column(nullable = false) private String ifscEncrypted;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    @Column(length = 500) private String rejectionReason;
    @Column(length = 120) private String paymentReference;
    @Column(columnDefinition = "char(36)") private UUID reviewedBy;
    private Instant reviewedAt;
    private Instant paidAt;
    private Instant closedAt;
    private Instant deletedAt;
}
