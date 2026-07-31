package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class HotCommentCacheService {
    private static final String REFRESH_CANDIDATES_KEY = "comment:hot-refresh:candidates";

    private final StringRedisTemplate redisTemplate;
    private final CommentProperties properties;

    public HotCommentCacheService(StringRedisTemplate redisTemplate, CommentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public List<Long> getHotCommentIds(Long videoId, int limit, long minLikes) {
        try {
            String value = redisTemplate.opsForValue().get(hotKey(videoId, limit, minLikes));
            if (value == null || value.isBlank()) return List.of();
            return java.util.Arrays.stream(value.split(","))
                    .map(Long::valueOf)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public void putHotCommentIds(Long videoId, int limit, long minLikes, List<Long> commentIds) {
        try {
            String value = commentIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            redisTemplate.opsForValue().set(hotKey(videoId, limit, minLikes), value,
                    properties.getHotCacheTtlSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException ignored) {
            // Redis is an optional cache; MySQL remains the fallback.
        }
    }

    public void markVideoForHotRefresh(Long videoId) {
        try {
            redisTemplate.opsForSet().add(REFRESH_CANDIDATES_KEY, videoId.toString());
        } catch (RuntimeException ignored) {
            // A later mutation can re-mark the video if Redis is temporarily unavailable.
        }
    }

    public Set<Long> popRefreshCandidates(int maxCount) {
        try {
            List<String> values = redisTemplate.opsForSet().pop(REFRESH_CANDIDATES_KEY, maxCount);
            if (values == null) return Set.of();
            return values.stream().map(Long::valueOf).collect(java.util.stream.Collectors.toSet());
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }

    private String hotKey(Long videoId, int limit, long minLikes) {
        return "comment:video:" + videoId + ":hot:" + limit + ":" + minLikes;
    }
}
