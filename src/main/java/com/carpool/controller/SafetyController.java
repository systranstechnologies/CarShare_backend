package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {
    private final UserBlockService userBlockService;
    private final OwnerProfileRepository ownerProfileRepository;

    @PostMapping("/blocked-owners/{ownerId}")
    public ApiResponse<?> blockOwner(@PathVariable UUID ownerId) {
        UUID userId = ownerProfileRepository.findById(ownerId).orElseThrow().getUser().getId();
        userBlockService.block(userId);
        return ApiResponse.of(Map.of("blocked", true));
    }

    @DeleteMapping("/blocked-owners/{ownerId}")
    public ApiResponse<?> unblockOwner(@PathVariable UUID ownerId) {
        UUID userId = ownerProfileRepository.findById(ownerId).orElseThrow().getUser().getId();
        userBlockService.unblock(userId);
        return ApiResponse.of(Map.of("blocked", false));
    }

    @GetMapping("/blocked-owners/{ownerId}")
    public ApiResponse<?> blockStatus(@PathVariable UUID ownerId) {
        UUID userId = ownerProfileRepository.findById(ownerId).orElseThrow().getUser().getId();
        return ApiResponse.of(Map.of("blocked", userBlockService.isBlockedByCurrentUser(userId)));
    }
}