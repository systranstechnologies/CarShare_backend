package com.carpool.repository;

import com.carpool.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<WalletTransaction> findTop100ByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
