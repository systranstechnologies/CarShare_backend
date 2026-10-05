package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.service.DiditWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/didit")
public class DiditWebhookController {
    private final DiditWebhookService webhookService;
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<?> webhook(
        @RequestHeader(value = "X-Signature-V2", required = false) String signatureV2,
        @RequestHeader(value = "X-Signature", required = false) String signature,
        @RequestHeader(value = "X-Signature-Simple", required = false) String signatureSimple,
        @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
        @RequestBody String payload) {
        webhookService.process(signatureV2, signature, signatureSimple, timestamp, payload);
        return ApiResponse.of(java.util.Map.of("status", "processed"));
    }
}
