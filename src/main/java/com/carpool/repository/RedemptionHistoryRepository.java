package com.carpool.repository;

import com.carpool.entity.RedemptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RedemptionHistoryRepository extends JpaRepository<RedemptionHistory, UUID> {
}
