package com.videosharing.videosharingspringboot.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisSessionService {

    private static final String SESSION_KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;

    public RedisSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void createSession(String sessionId, Long userId, Duration lifetime) {
        redisTemplate.opsForValue().set(key(sessionId), userId.toString(), lifetime.toHours(), TimeUnit.HOURS);
    }

    public boolean isSessionActive(String sessionId, Long userId) {
        try {
            String storedUserId = redisTemplate.opsForValue().get(key(sessionId));
            return userId.toString().equals(storedUserId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void deleteSession(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
