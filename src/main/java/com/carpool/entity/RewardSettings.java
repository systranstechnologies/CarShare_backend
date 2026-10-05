package com.carpool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reward_settings")
public class RewardSettings extends BaseEntity {
    @Id private Short id = 1;
    @Column(nullable = false) private boolean referralEnabled = true;
    @Column(nullable = false) private long coinsPerReferral = 100;
    @Column(nullable = false) private long minimumRedemption = 100;
    @Column(nullable = false) private long maximumRedemption = 10000;
    @Column(nullable = false) private int subscriptionCoinPercentage = 50;
    @Column(nullable = false, length = 255) private String referralBaseUrl = "https://carshare247.com/register";
}
