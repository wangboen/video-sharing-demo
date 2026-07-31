package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotCommentCacheServiceTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void writesOrderedIdsToTheConfiguredKeyWithConfiguredTtl() {
        CommentProperties properties = new CommentProperties();
        properties.setHotCacheTtlSeconds(90);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        HotCommentCacheService service = new HotCommentCacheService(redisTemplate, properties);

        service.putHotCommentIds(42L, 3, 10L, List.of(101L, 99L, 88L));

        verify(valueOperations).set("comment:video:42:hot:3:10", "101,99,88", 90, TimeUnit.SECONDS);
    }
}
