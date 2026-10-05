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
public class DiditEventDTO {
    private UUID id;
    private String eventType;
    private Map<String, Object> eventData;
    private LocalDateTime eventTimestamp;
}
