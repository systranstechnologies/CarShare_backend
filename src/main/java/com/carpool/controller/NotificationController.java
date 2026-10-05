package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.entity.Role;
import com.carpool.exception.AppException;
import com.carpool.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Locale;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam String role) {
        return ApiResponse.of(notificationService.myNotifications(parseRole(role)));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<?> markRead(@PathVariable UUID notificationId, @RequestParam String role) {
        notificationService.markRead(notificationId, parseRole(role));
        return ApiResponse.of(java.util.Map.of("status", "ok"));
    }

    @PatchMapping("/read-all")
    public ApiResponse<?> markAllRead(@RequestParam String role) {
        notificationService.markAllRead(parseRole(role));
        return ApiResponse.of(java.util.Map.of("status", "ok"));
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid notification role");
        }
    }
}
