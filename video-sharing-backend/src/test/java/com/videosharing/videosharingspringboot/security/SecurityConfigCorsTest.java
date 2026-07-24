package com.videosharing.videosharingspringboot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WebMvcTest(controllers = SecurityConfigCorsTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigCorsTest.ProbeController.class})
class SecurityConfigCorsTest {
    @Autowired CorsConfigurationSource corsConfigurationSource;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void bindsDefaultOriginsFromApplicationPropertiesWithoutCredentials() {
        CorsConfiguration cors = corsForBoundConfiguration();

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

    private CorsConfiguration corsForBoundConfiguration() {
        var source = (UrlBasedCorsConfigurationSource) corsConfigurationSource;
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/videos"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/cors-probe")
        ResponseEntity<Void> probe() {
            return ResponseEntity.ok().build();
        }
    }
}
