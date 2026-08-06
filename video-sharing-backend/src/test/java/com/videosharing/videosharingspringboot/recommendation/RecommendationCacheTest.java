package com.videosharing.videosharingspringboot.recommendation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationCacheTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void cachesEmptyIdListsWithTtlJitter() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RecommendationCache cache = new RecommendationCache(redisTemplate, true, 30);

        cache.putIdList("rec:pool:hot", List.of(), Duration.ofSeconds(60));

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(org.mockito.Mockito.eq("rec:pool:hot"), org.mockito.Mockito.eq(""),
                ttl.capture(), org.mockito.Mockito.eq(TimeUnit.SECONDS));
        assertThat(ttl.getValue()).isBetween(60L, 90L);
    }

    @Test
    void readsCachedIdListsInOrder() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rec:pool:hot")).thenReturn("7,5,3");
        RecommendationCache cache = new RecommendationCache(redisTemplate, true, 0);

        Optional<List<Long>> ids = cache.getIdList("rec:pool:hot");

        assertThat(ids).contains(List.of(7L, 5L, 3L));
    }

    @Test
    void disabledCacheDoesNotTouchRedis() {
        RecommendationCache cache = new RecommendationCache(redisTemplate, false, 30);

        assertThat(cache.getIdList("rec:pool:hot")).isEmpty();
        cache.putIdList("rec:pool:hot", List.of(1L), Duration.ofSeconds(60));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void redisReadFailureReturnsEmptyOptional() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rec:pool:hot")).thenThrow(new RuntimeException("redis unavailable"));
        RecommendationCache cache = new RecommendationCache(redisTemplate, true, 0);

        assertThat(cache.getIdList("rec:pool:hot")).isEmpty();
    }

    @Test
    void redisWriteFailureIsBestEffort() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis unavailable"))
                .when(valueOperations).set("rec:pool:hot", "1", 60L, TimeUnit.SECONDS);
        RecommendationCache cache = new RecommendationCache(redisTemplate, true, 0);

        cache.putIdList("rec:pool:hot", List.of(1L), Duration.ofSeconds(60));
    }
}
