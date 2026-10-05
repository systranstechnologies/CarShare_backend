package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.entity.Role;
import com.carpool.exception.AppException;
import com.carpool.mapper.RideMapper;
import com.carpool.repository.RideRepository;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rides")
@RequiredArgsConstructor
public class AdminRideController {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final AuthFacade authFacade;

    @GetMapping
    public ApiResponse<?> list() {
        if (authFacade.currentUser().getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
        }
        return ApiResponse.of(rideRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(rideMapper::toResponse)
            .toList());
    }
}