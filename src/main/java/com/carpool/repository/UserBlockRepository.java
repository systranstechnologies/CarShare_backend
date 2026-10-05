package com.carpool.repository;

import com.carpool.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}