package com.carpool.repository;

import com.carpool.entity.DiditSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DiditSessionRepository extends JpaRepository<DiditSession, UUID> {
    Optional<DiditSession> findBySessionId(String sessionId);
}
