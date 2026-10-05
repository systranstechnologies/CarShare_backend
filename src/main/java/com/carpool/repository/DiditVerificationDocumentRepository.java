package com.carpool.repository;

import com.carpool.entity.DiditVerificationDocument;
import com.carpool.entity.DiditVerificationDocument.DocumentSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiditVerificationDocumentRepository extends JpaRepository<DiditVerificationDocument, UUID> {

    List<DiditVerificationDocument> findByDiditVerificationId(UUID diditVerificationId);

    Optional<DiditVerificationDocument> findByDiditVerificationIdAndDocumentSide(UUID diditVerificationId, DocumentSide side);
}
