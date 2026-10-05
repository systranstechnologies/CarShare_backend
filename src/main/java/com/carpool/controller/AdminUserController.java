package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.admin.AdminUserView;
import com.carpool.entity.Role;
import com.carpool.exception.AppException;
import com.carpool.repository.UserRepository;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserRepository userRepository;
    private final AuthFacade authFacade;

    @GetMapping
    public ApiResponse<List<AdminUserView>> list(@RequestParam(required = false) Role role) {
        if (authFacade.currentUser().getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
        }
        List<AdminUserView> users = userRepository.findAll().stream()
            .filter(user -> user.getRole() != Role.ADMIN)
            .filter(user -> role == null || user.getRole() == role)
            .sorted((left, right) -> {
                if (left.getCreatedAt() == null && right.getCreatedAt() == null) return 0;
                if (left.getCreatedAt() == null) return 1;
                if (right.getCreatedAt() == null) return -1;
                return right.getCreatedAt().compareTo(left.getCreatedAt());
            })
            .map(user -> AdminUserView.builder()
                .id(user.getId())
                .name(user.getFullName())
                .mobile(user.getMobile())
                .role(user.getRole())
                .gender(user.getGender())
                .active(user.isActive())
                .mobileVerified(user.isMobileVerified())
                .verificationStatus(user.getVerificationStatus())
                .createdAt(user.getCreatedAt())
                .build())
            .toList();
        return ApiResponse.of(users);
    }
}
