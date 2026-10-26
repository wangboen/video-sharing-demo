package com.videosharing.videosharingspringboot.security;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class RefreshTokenService {
    private static final String KEY_PREFIX = "auth:refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); " +
                    "if value then redis.call('DEL', KEYS[1]); end; return value",
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisSessionService sessions;
    private final Duration lifetime;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            RedisSessionService sessions,
            @Value("${auth.refresh-token-expiration-ms:1209600000}") long lifetimeMs) {
        this.redisTemplate = redisTemplate;
        this.sessions = sessions;
        this.lifetime = Duration.ofMillis(lifetimeMs);
    }

    public String issue(Long userId, String accountType, String accessSessionId) {
        String rawToken = newToken();
        long expiresAt = Instant.now().plus(lifetime).getEpochSecond();
        String value = String.join("|", userId.toString(), accountType, accessSessionId, Long.toString(expiresAt));
        redisTemplate.opsForValue().set(key(rawToken), value, lifetime);
        return rawToken;
    }

    public Session consume(String rawToken, String expectedAccountType) {
        if (rawToken == null || rawToken.isBlank()) {
            throw authenticationRequired();
        }
        String stored = consumeKey(rawToken);
        if (stored == null) {
            throw authenticationRequired();
        }

        String[] fields = stored.split("\\|", -1);
        if (fields.length != 4) {
            throw authenticationRequired();
        }

        Long userId;
        long expiresAt;
        try {
            userId = Long.valueOf(fields[0]);
            expiresAt = Long.parseLong(fields[3]);
        } catch (NumberFormatException exception) {
            throw authenticationRequired();
        }
        if (!expectedAccountType.equals(fields[1]) || expiresAt <= Instant.now().getEpochSecond()) {
            sessions.deleteSession(fields[2]);
            throw authenticationRequired();
        }

        sessions.deleteSession(fields[2]);
        return new Session(userId, fields[1]);
    }

    public void revoke(String rawToken, String expectedAccountType) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String stored = consumeKey(rawToken);
        if (stored == null) {
            return;
        }
        String[] fields = stored.split("\\|", -1);
        if (fields.length == 4 && expectedAccountType.equals(fields[1])) {
            sessions.deleteSession(fields[2]);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String consumeKey(String rawToken) {
        return redisTemplate.execute(GET_AND_DELETE, List.of(key(rawToken)));
    }

    private ApiExceptionHandler.AuthenticationRequiredException authenticationRequired() {
        return new ApiExceptionHandler.AuthenticationRequiredException("Refresh session is invalid or expired");
    }

    public record Session(Long userId, String accountType) {}
}
