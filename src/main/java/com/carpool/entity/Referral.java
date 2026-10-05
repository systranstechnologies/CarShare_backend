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
@Table(name = "referrals", uniqueConstraints = @UniqueConstraint(name = "uk_referral_referred", columnNames = "referred_user_id"))
public class Referral extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private User referrer;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false)
    private User referredUser;

    @Enumerated(EnumType.STRING) @Column(name = "referred_role", nullable = false, length = 20)
    private Role referredRole;

    @Column(name = "referral_code", nullable = false, length = 20, updatable = false)
    private String referralCode;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private long coinsAwarded;

    @Column(nullable = false, updatable = false)
    private Instant registeredAt;
    private Instant activityCompletedAt;
    private Instant qualifiedAt;
    private Instant rewardedAt;
    @Column(length = 500) private String fraudReason;
    @Column(length = 64) private String deviceFingerprintHash;
    @Column(length = 64) private String paymentProfileHash;
    private Instant deletedAt;
}
