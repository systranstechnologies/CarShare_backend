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
@Table(name = "user_wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallet_user", columnNames = "user_id"))
public class UserWallet extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false) private long availableCoins;
    @Column(nullable = false) private long pendingCoins;
    @Column(nullable = false) private long redemptionPendingCoins;
    @Column(nullable = false) private long totalEarnedCoins;
    @Column(nullable = false) private long usedCoins;
    @Column(nullable = false) private long redeemedCoins;
    @Column(nullable = false) private long expiredCoins;
    @Version private long version;
    private Instant deletedAt;
}
