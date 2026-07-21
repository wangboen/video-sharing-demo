package com.videosharing.videosharingspringboot.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisSessionService sessionService = new RedisSessionService(redisTemplate);

    RedisSessionServiceTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesUserSessionWithTheJwtLifetime() {
        sessionService.createSession("session-1", 42L, Duration.ofHours(2));

        verify(valueOperations).set("auth:session:session-1", "42", 2, java.util.concurrent.TimeUnit.HOURS);
    }

    @Test
    void acceptsOnlyTheSessionOwnedByTheAuthenticatedUser() {
        when(valueOperations.get("auth:session:session-1")).thenReturn("42");

        assertTrue(sessionService.isSessionActive("session-1", 42L));
        assertFalse(sessionService.isSessionActive("session-1", 7L));
    }

    @Test
    void rejectsMissingSessions() {
        when(valueOperations.get("auth:session:missing-session")).thenReturn(null);

        assertFalse(sessionService.isSessionActive("missing-session", 42L));
    }
}
