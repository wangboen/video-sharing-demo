package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class HotCommentCacheService {
    private static final Logger log = LoggerFactory.getLogger(HotCommentCacheService.class);
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
        String value = commentIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        redisTemplate.opsForValue().set(hotKey(videoId, limit, minLikes), value,
                properties.getHotCacheTtlSeconds(), TimeUnit.SECONDS);
    }

    public void markVideoForHotRefresh(Long videoId) {
        try {
            markVideoForHotRefreshOrThrow(videoId);
        } catch (RuntimeException exception) {
            log.warn("Failed to mark video for hot comment refresh videoId={}", videoId, exception);
        }
    }

    public void markVideoForHotRefreshOrThrow(Long videoId) {
        redisTemplate.opsForSet().add(REFRESH_CANDIDATES_KEY, videoId.toString());
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
