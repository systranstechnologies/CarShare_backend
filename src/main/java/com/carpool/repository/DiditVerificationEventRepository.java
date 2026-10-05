package com.carpool.repository;

import com.carpool.entity.DiditVerificationEvent;
import com.carpool.entity.DiditVerificationEvent.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiditVerificationEventRepository extends JpaRepository<DiditVerificationEvent, UUID> {

    List<DiditVerificationEvent> findByDiditVerificationId(UUID diditVerificationId, Sort sort);

    List<DiditVerificationEvent> findByDiditVerificationIdAndEventType(UUID diditVerificationId, EventType eventType);

    Long countByDiditVerificationIdAndEventType(UUID diditVerificationId, EventType eventType);
}
