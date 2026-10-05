package com.carpool.repository;

import com.carpool.entity.DiditWebhookEvent;
import com.carpool.entity.DiditWebhookEvent.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiditWebhookEventRepository extends JpaRepository<DiditWebhookEvent, UUID> {

    Optional<DiditWebhookEvent> findBySessionId(String sessionId);

    Optional<DiditWebhookEvent> findByIdempotencyKey(String idempotencyKey);

    Page<DiditWebhookEvent> findByProcessingStatus(ProcessingStatus status, Pageable pageable);

    Long countByProcessingStatus(ProcessingStatus status);
}
