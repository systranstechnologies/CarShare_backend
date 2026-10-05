package com.carpool.repository;

import com.carpool.entity.RedemptionRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RedemptionRequestRepository extends JpaRepository<RedemptionRequest, UUID> {
    List<RedemptionRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
    @EntityGraph(attributePaths = "user")
    List<RedemptionRequest> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = "user")
    @Query("select r from RedemptionRequest r join fetch r.user where r.id = :id")
    Optional<RedemptionRequest> findByIdWithUser(@Param("id") UUID id);
}
