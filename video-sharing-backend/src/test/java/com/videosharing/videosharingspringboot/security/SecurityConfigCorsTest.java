package com.videosharing.videosharingspringboot.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigCorsTest {
    @Test
    void defaultsToBothLocalFrontendOriginsWithoutCredentials() {
        CorsConfiguration cors = corsFor("http://localhost:5173,http://localhost:5174");

        assertEquals(List.of("http://localhost:5173", "http://localhost:5174"), cors.getAllowedOrigins());
        assertFalse(Boolean.TRUE.equals(cors.getAllowCredentials()));
    }

    @Test
    void acceptsConfiguredOriginsButRejectsWildcardOrigins() {
        CorsConfiguration cors = corsFor("https://videos.example.test, https://staff.example.test");

        assertEquals(List.of("https://videos.example.test", "https://staff.example.test"), cors.getAllowedOrigins());
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfig(null, "*"));
    }

    private CorsConfiguration corsFor(String origins) {
        var source = (UrlBasedCorsConfigurationSource) new SecurityConfig(null, origins).corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/videos"));
    }
}
