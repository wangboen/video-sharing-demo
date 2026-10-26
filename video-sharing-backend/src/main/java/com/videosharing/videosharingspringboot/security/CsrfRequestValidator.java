package com.videosharing.videosharingspringboot.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CsrfRequestValidator {
    private final Set<String> allowedOrigins;

    public CsrfRequestValidator(
            @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isValid(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String csrfHeader = request.getHeader("X-CSRF-Guard");
        return origin != null && allowedOrigins.contains(origin) && "1".equals(csrfHeader);
    }
}
