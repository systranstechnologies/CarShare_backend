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
@Table(name = "referral_profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_referral_profile_user", columnNames = "user_id"),
    @UniqueConstraint(name = "uk_referral_profile_code", columnNames = "referral_code")
})
public class ReferralProfile extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator @Column(columnDefinition = "char(36)")
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "referral_code", nullable = false, length = 20, updatable = false)
    private String referralCode;

    private Instant deletedAt;
}
