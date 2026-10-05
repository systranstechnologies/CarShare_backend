package com.carpool.repository;

import com.carpool.entity.Subscription;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<Subscription> findByProviderPaymentId(String providerPaymentId);

    @Query("select s from Subscription s join fetch s.owner o join fetch o.user join fetch s.plan")
    List<Subscription> findAllWithOwnerAndPlan();
}
