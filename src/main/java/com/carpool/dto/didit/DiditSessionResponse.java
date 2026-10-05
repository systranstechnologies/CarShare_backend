package com.carpool.dto.didit;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class DiditSessionResponse {
    private String sessionId;
    private String verificationUrl;
    private UUID userId;
    private String role;
    private String status;
}
