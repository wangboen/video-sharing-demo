package com.videosharing.videosharingspringboot.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RecommendationCache {
    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final long ttlJitterSeconds;

    public RecommendationCache(StringRedisTemplate redisTemplate,
                               @Value("${recommendation.cache.enabled:true}") boolean enabled,
                               @Value("${recommendation.cache.ttl-jitter-seconds:30}") long ttlJitterSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.ttlJitterSeconds = Math.max(0, ttlJitterSeconds);
    }

    public Optional<List<Long>> getIdList(String key) {
        return getString(key).map(RecommendationCache::parseIds);
    }

    public void putIdList(String key, List<Long> ids, Duration ttl) {
        String value = ids == null ? "" : ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        putString(key, value, ttl);
    }

    public Optional<String> getString(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void putString(String key, String value, Duration ttl) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value == null ? "" : value, ttlSeconds(ttl), TimeUnit.SECONDS);
        } catch (RuntimeException ignored) {
            // Recommendation cache writes are best-effort; providers can query the database on misses.
        }
    }

    private long ttlSeconds(Duration ttl) {
        long base = Math.max(1, ttl == null ? 1 : ttl.toSeconds());
        if (ttlJitterSeconds <= 0) {
            return base;
        }
        return base + ThreadLocalRandom.current().nextLong(ttlJitterSeconds + 1);
    }

    private static List<Long> parseIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .filter(part -> !part.isBlank())
                .map(Long::valueOf)
                .toList();
    }
}
