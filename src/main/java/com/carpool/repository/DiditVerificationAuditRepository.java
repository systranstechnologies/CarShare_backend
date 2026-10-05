package com.carpool.repository;

import com.carpool.entity.DiditVerificationAudit;
import com.carpool.entity.Role;
import com.carpool.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiditVerificationAuditRepository extends JpaRepository<DiditVerificationAudit, UUID> {
    Optional<DiditVerificationAudit> findFirstBySessionId(String sessionId);
    Optional<DiditVerificationAudit> findFirstBySessionIdOrderByUpdatedAtDesc(String sessionId);
    List<DiditVerificationAudit> findAllByOrderByCreatedAtDesc();
    List<DiditVerificationAudit> findAllByOrderByUpdatedAtDesc();
    List<DiditVerificationAudit> findByUserRoleOrderByCreatedAtDesc(Role role);
    List<DiditVerificationAudit> findByStatusOrderByCreatedAtDesc(VerificationStatus status);
    List<DiditVerificationAudit> findByUserRoleAndStatusOrderByCreatedAtDesc(Role role, VerificationStatus status);
}
