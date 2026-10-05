package com.carpool.dto.didit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiditDocumentDTO {
    private UUID id;
    private String documentSide; // FRONT, BACK, SELFIE
    private String imageUrl;
    private Integer documentWidth;
    private Integer documentHeight;
    private Map<String, Object> processedData;
    private LocalDateTime uploadedAt;
}
