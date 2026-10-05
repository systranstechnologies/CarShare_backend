package com.carpool.repository;

import com.carpool.entity.Referral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {
    @Override
    @EntityGraph(attributePaths = {"referrer", "referredUser"})
    List<Referral> findAll();

    Optional<Referral> findByReferredUserId(UUID referredUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.referredUser.id = :referredUserId")
    Optional<Referral> findByReferredUserIdForUpdate(@Param("referredUserId") UUID referredUserId);
    @EntityGraph(attributePaths = "referredUser")
    List<Referral> findByReferrerIdOrderByRegisteredAtDesc(UUID referrerId);
    long countByReferrerIdAndStatus(UUID referrerId, String status);
    boolean existsByDeviceFingerprintHashAndReferrerId(String deviceFingerprintHash, UUID referrerId);
}
