package com.carpool.repository;

import com.carpool.entity.ReferralProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReferralProfileRepository extends JpaRepository<ReferralProfile, UUID> {
    Optional<ReferralProfile> findByUserId(UUID userId);
    Optional<ReferralProfile> findByReferralCodeIgnoreCase(String referralCode);
    boolean existsByReferralCodeIgnoreCase(String referralCode);
}
