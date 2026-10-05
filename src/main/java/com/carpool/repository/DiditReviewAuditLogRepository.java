package com.carpool.repository;

import com.carpool.entity.DiditReviewAuditLog;
import com.carpool.entity.DiditReviewAuditLog.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DiditReviewAuditLogRepository extends JpaRepository<DiditReviewAuditLog, UUID> {

    List<DiditReviewAuditLog> findByDiditVerificationId(UUID diditVerificationId);

    Page<DiditReviewAuditLog> findByDiditVerificationId(UUID diditVerificationId, Pageable pageable);

    Page<DiditReviewAuditLog> findByAdminId(UUID adminId, Pageable pageable);

    Page<DiditReviewAuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<DiditReviewAuditLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Long countByDiditVerificationId(UUID diditVerificationId);
}
