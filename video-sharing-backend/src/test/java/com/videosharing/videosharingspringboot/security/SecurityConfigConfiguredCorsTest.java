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
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(
        controllers = SecurityConfigConfiguredCorsTest.ProbeController.class,
        properties = "cors.allowed-origins=https://videos.example.test,https://staff.example.test"
)
@Import({SecurityConfig.class, SecurityConfigConfiguredCorsTest.ProbeController.class})
class SecurityConfigConfiguredCorsTest {
    @Autowired CorsConfigurationSource corsConfigurationSource;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void bindsConfiguredOriginsFromTheEnvironment() {
        var source = (UrlBasedCorsConfigurationSource) corsConfigurationSource;
        var cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/videos"));

        assertEquals(List.of("https://videos.example.test", "https://staff.example.test"), cors.getAllowedOrigins());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/configured-cors-probe")
        ResponseEntity<Void> probe() {
            return ResponseEntity.ok().build();
        }
    }
}
