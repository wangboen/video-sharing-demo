package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotCommentRefreshJobTest {
    @Mock private HotCommentCacheService cache;
    @Mock private CommentRelationRepository relations;

    @Test
    void refreshesCandidateVideoWithMysqlHotOrder() {
        CommentProperties properties = new CommentProperties();
        properties.setHotRefreshBatchSize(2);
        properties.setFeaturedLimit(3);
        properties.setFeaturedMinLikes(10L);
        HotCommentRefreshJob job = new HotCommentRefreshJob(cache, relations, properties);
        CommentRelation hottest = relation(101L, 50L, "2026-07-31T10:00:00Z");
        CommentRelation next = relation(99L, 40L, "2026-07-31T09:00:00Z");
        when(cache.popRefreshCandidates(2)).thenReturn(Set.of(42L));
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(hottest, next));

        job.refreshHotComments();

        verify(cache).putHotCommentIds(42L, 3, 10L, List.of(101L, 99L));
    }

    @Test
    void requeuesCandidateWhenRefreshFails() {
        CommentProperties properties = new CommentProperties();
        properties.setHotRefreshBatchSize(2);
        properties.setFeaturedLimit(3);
        properties.setFeaturedMinLikes(10L);
        HotCommentRefreshJob job = new HotCommentRefreshJob(cache, relations, properties);
        when(cache.popRefreshCandidates(2)).thenReturn(Set.of(42L));
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class)))
                .thenThrow(new RuntimeException("mysql unavailable"));

        job.refreshHotComments();

        verify(cache).markVideoForHotRefresh(42L);
    }

    @Test
    void requeuesCandidateWhenRedisWriteFails() {
        CommentProperties properties = new CommentProperties();
        properties.setHotRefreshBatchSize(2);
        properties.setFeaturedLimit(3);
        properties.setFeaturedMinLikes(10L);
        HotCommentRefreshJob job = new HotCommentRefreshJob(cache, relations, properties);
        CommentRelation hottest = relation(101L, 50L, "2026-07-31T10:00:00Z");
        when(cache.popRefreshCandidates(2)).thenReturn(Set.of(42L));
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(hottest));
        doThrow(new RuntimeException("redis unavailable"))
                .when(cache).putHotCommentIds(42L, 3, 10L, List.of(101L));

        job.refreshHotComments();

        verify(cache).markVideoForHotRefresh(42L);
    }

    private CommentRelation relation(Long id, Long likes, String createdAt) {
        CommentRelation relation = new CommentRelation();
        relation.setCommentId(id);
        relation.setLikeCount(likes);
        relation.setCreatedAt(Instant.parse(createdAt));
        return relation;
    }
}
