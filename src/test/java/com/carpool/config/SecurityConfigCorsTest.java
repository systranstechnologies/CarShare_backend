package com.carpool.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityConfigCorsTest {

    @Test
    void allowsProductionFrontendOriginButRejectsUnlistedOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null);
        CorsConfiguration corsConfiguration = securityConfig
            .corsConfigurationSource(new AppProperties())
            .getCorsConfiguration(request("https://carshare247.online"));
        CorsConfiguration unlistedOriginConfiguration = securityConfig
            .corsConfigurationSource(new AppProperties())
            .getCorsConfiguration(request("https://untrusted.example"));

        assertEquals("https://carshare247.online", corsConfiguration.checkOrigin("https://carshare247.online"));
        assertNull(unlistedOriginConfiguration.checkOrigin("https://untrusted.example"));
    }

    private HttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/register");
        request.addHeader("Origin", origin);
        return request;
    }
}
