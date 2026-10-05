package com.carpool.repository;

import com.carpool.entity.DiditVerification;
import com.carpool.entity.DiditVerification.DiditReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiditVerificationRepository extends JpaRepository<DiditVerification, UUID> {

    Optional<DiditVerification> findBySessionId(String sessionId);

    Optional<DiditVerification> findByUserId(UUID userId);

    Page<DiditVerification> findByCurrentStatus(DiditReviewStatus status, Pageable pageable);

    Page<DiditVerification> findByCurrentStatusAndDocumentCountry(DiditReviewStatus status, String country, Pageable pageable);

    @Query("SELECT d FROM DiditVerification d WHERE d.currentStatus = 'UNDER_REVIEW' AND d.approvalStatus IS NULL")
    Page<DiditVerification> findPendingReviews(Pageable pageable);

    @Query("SELECT d FROM DiditVerification d WHERE d.approvalStatus = 'APPROVED'")
    Page<DiditVerification> findApprovedReviews(Pageable pageable);

    @Query("SELECT d FROM DiditVerification d WHERE d.approvalStatus = 'DECLINED'")
    Page<DiditVerification> findDeclinedReviews(Pageable pageable);

    @Query("SELECT d FROM DiditVerification d WHERE d.currentStatus = 'RESUBMISSION_REQUESTED'")
    Page<DiditVerification> findResubmissions(Pageable pageable);

    @Query("SELECT d FROM DiditVerification d WHERE d.currentStatus = :status AND d.createdAt BETWEEN :startDate AND :endDate")
    Page<DiditVerification> findByStatusAndDateRange(
        @Param("status") DiditReviewStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    @Query("SELECT d FROM DiditVerification d WHERE d.amlRiskLevel = :riskLevel AND d.currentStatus = 'UNDER_REVIEW'")
    Page<DiditVerification> findByRiskLevelAndUnderReview(
        @Param("riskLevel") String riskLevel,
        Pageable pageable
    );

    Long countByCurrentStatus(DiditReviewStatus status);

    Long countByCurrentStatusAndDocumentCountry(DiditReviewStatus status, String country);
}
