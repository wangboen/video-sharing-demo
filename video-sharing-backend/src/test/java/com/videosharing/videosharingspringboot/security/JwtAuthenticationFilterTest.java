package com.videosharing.videosharingspringboot.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final RedisSessionService redisSessionService = mock(RedisSessionService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, redisSessionService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateWhenTheRedisSessionIsMissing() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        mockValidJwt();
        when(redisSessionService.isSessionActive("session-1", 42L)).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticatesOnlyWhenTheRedisSessionMatchesTheJwtUser() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        mockValidJwt();
        when(redisSessionService.isSessionActive("session-1", 42L)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertEquals("creator", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed-jwt");
        return request;
    }

    private void mockValidJwt() {
        when(jwtService.isTokenValid("signed-jwt")).thenReturn(true);
        when(jwtService.extractUsername("signed-jwt")).thenReturn("creator");
        when(jwtService.extractSessionId("signed-jwt")).thenReturn("session-1");
        when(jwtService.extractUserId("signed-jwt")).thenReturn(42L);
    }
}
