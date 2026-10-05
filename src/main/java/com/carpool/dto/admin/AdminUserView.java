package com.carpool.dto.admin;

import com.carpool.entity.Role;
import com.carpool.entity.VerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AdminUserView {
    private UUID id;
    private String name;
    private String mobile;
    private Role role;
    private String gender;
    private boolean active;
    private boolean mobileVerified;
    private VerificationStatus verificationStatus;
    private Instant createdAt;
}
