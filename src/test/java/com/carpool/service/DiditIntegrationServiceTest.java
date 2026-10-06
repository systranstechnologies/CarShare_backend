package com.carpool.service;

import com.carpool.config.DiditProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiditIntegrationServiceTest {

    @Test
    void sendsStatusUpdateAsJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/session/session-123/update-status/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            DiditProperties properties = new DiditProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setApiKey("test-api-key");
            DiditIntegrationService service = new DiditIntegrationService(
                properties, null, null, null, null, null, objectMapper, null);

            service.updateSessionStatus("session-123", "Approved", "Reviewed");

            JsonNode payload = objectMapper.readTree(requestBody.get());
            assertEquals("application/json", contentType.get());
            assertEquals("Approved", payload.path("new_status").asText());
            assertEquals("Reviewed", payload.path("comment").asText());
        } finally {
            server.stop(0);
        }
    }
}
