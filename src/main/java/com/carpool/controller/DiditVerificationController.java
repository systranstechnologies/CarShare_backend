package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.entity.Role;
import com.carpool.service.DiditIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.carpool.security.AuthFacade;
import com.carpool.repository.UserRepository;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/didit")
public class DiditVerificationController {
    private final DiditIntegrationService integrationService;
    private final AuthFacade authFacade;
    private final UserRepository userRepository;

    @PostMapping("/session")
    public ApiResponse<?> createSession(@RequestParam Role role,
                                        @RequestParam(defaultValue = "false") boolean nativeApp) {
        return ApiResponse.of(integrationService.createSession(role, nativeApp));
    }

    @GetMapping("/status")
    public ApiResponse<?> status() {
        integrationService.syncCurrentStatus();
        var user = userRepository.findById(authFacade.currentUser().getUserId()).orElseThrow();
        return ApiResponse.of(java.util.Map.of("status", user.getVerificationStatus().name(), "sessionId", user.getDiditSessionId() == null ? "" : user.getDiditSessionId()));
    }
}
