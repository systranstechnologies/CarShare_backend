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
@Table(name = "wallet_transactions", uniqueConstraints = @UniqueConstraint(name = "uk_wallet_transaction_idempotency", columnNames = "idempotency_key"))
public class WalletTransaction extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private UserWallet wallet;

    @Column(nullable = false, length = 40) private String transactionType;
    @Column(nullable = false) private long coinsAdded;
    @Column(nullable = false) private long coinsDeducted;
    @Column(nullable = false) private long balanceAfter;
    @Column(length = 40) private String referenceType;
    @Column(columnDefinition = "char(36)") private UUID referenceId;
    @Column(nullable = false, unique = true, length = 160) private String idempotencyKey;
    @Column(length = 500) private String remarks;
    private Instant deletedAt;
}
