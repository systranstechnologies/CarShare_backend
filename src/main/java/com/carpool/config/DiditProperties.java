package com.carpool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.didit")
public class DiditProperties {
    private String baseUrl;
    private String apiKey;
    private String workflowId;
    private String webhookSecret;
    private String callbackBaseUrl;
    private String nativeCallbackBaseUrl;
}
