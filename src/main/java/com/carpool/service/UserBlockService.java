package com.carpool.service;

import com.carpool.entity.User;
import com.carpool.entity.UserBlock;
import com.carpool.exception.AppException;
import com.carpool.repository.UserBlockRepository;
import com.carpool.repository.UserRepository;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBlockService {
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final AuthFacade authFacade;
    private final AuditService auditService;

    @Transactional
    public void block(UUID blockedUserId) {
        UUID blockerId = authFacade.currentUser().getUserId();
        if (blockerId.equals(blockedUserId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_BLOCK", "You cannot block yourself");
        }
        User blocker = userRepository.findById(blockerId).orElseThrow();
        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedUserId)) {
            UserBlock block = new UserBlock();
            block.setBlocker(blocker);
            block.setBlocked(blocked);
            userBlockRepository.save(block);
            auditService.log("USER_BLOCKED", blockerId.toString(), blockedUserId.toString(), "{}" );
        }
    }

    @Transactional
    public void unblock(UUID blockedUserId) {
        UUID blockerId = authFacade.currentUser().getUserId();
        userBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedUserId);
        auditService.log("USER_UNBLOCKED", blockerId.toString(), blockedUserId.toString(), "{}" );
    }

    public boolean isBlockedBetween(UUID firstUserId, UUID secondUserId) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(firstUserId, secondUserId)
                || userBlockRepository.existsByBlockerIdAndBlockedId(secondUserId, firstUserId);
    }

    public boolean isBlockedByCurrentUser(UUID blockedUserId) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(authFacade.currentUser().getUserId(), blockedUserId);
    }
}