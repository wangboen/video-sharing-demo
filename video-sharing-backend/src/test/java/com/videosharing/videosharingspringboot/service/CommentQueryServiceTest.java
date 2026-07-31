package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {
    @Mock private CommentRelationRepository relations;
    @Mock private CommentContentRepository contents;
    @Mock private CommentLikeRepository likes;
    @Mock private HotCommentCacheService hotCache;

    @Test
    void composesFeaturedAndNormalCommentsInMysqlOrder() {
        CommentProperties properties = properties();
        CommentQueryService service = new CommentQueryService(relations, contents, likes, hotCache, properties);
        CommentRelation featured = relation(10L, 100L, false, "2026-07-31T12:00:00Z");
        CommentRelation duplicateFeatured = relation(10L, 100L, false, "2026-07-31T12:00:00Z");
        CommentRelation newestNormal = relation(30L, 5L, false, "2026-07-31T11:00:00Z");
        CommentRelation olderNormal = relation(20L, 3L, false, "2026-07-31T10:00:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of(featured));
        when(relations.findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(duplicateFeatured, newestNormal, olderNormal));
        when(contents.findAllById(List.of(10L, 30L, 20L)))
                .thenReturn(List.of(content(20L, "older"), content(10L, "featured"), content(30L, "newest")));

        var response = service.getVideoComments(42L, null, 3, null);

        assertThat(response.featured()).extracting(comment -> comment.commentId()).containsExactly(10L);
        assertThat(response.comments()).extracting(comment -> comment.commentId()).containsExactly(30L, 20L);
        assertThat(response.featured().get(0).content()).isEqualTo("featured");
        assertThat(response.comments()).extracting(comment -> comment.content()).containsExactly("newest", "older");
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void displaysAnonymizedCommentsWithoutExposingUserId() {
        CommentProperties properties = properties();
        CommentQueryService service = new CommentQueryService(relations, contents, likes, hotCache, properties);
        CommentRelation anonymous = relation(10L, 3L, true, "2026-07-31T12:00:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(relations.findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(anonymous));
        when(contents.findAllById(List.of(10L))).thenReturn(List.of(content(10L, "alice")));

        var response = service.getVideoComments(42L, null, 20, null);

        assertThat(response.comments()).singleElement().satisfies(comment -> {
            assertThat(comment.username()).isEqualTo("匿名用户");
            assertThat(comment.anonymized()).isTrue();
        });
        assertThat(response.comments().get(0).getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId");
    }

    private CommentProperties properties() {
        CommentProperties properties = new CommentProperties();
        properties.setFeaturedLimit(3);
        properties.setFeaturedMinLikes(10L);
        properties.setPageSizeDefault(20);
        properties.setPageSizeMax(50);
        return properties;
    }

    private CommentRelation relation(Long id, Long likeCount, boolean anonymized, String createdAt) {
        CommentRelation relation = new CommentRelation();
        relation.setCommentId(id);
        relation.setLikeCount(likeCount);
        relation.setAnonymized(anonymized);
        relation.setCreatedAt(Instant.parse(createdAt));
        return relation;
    }

    private CommentContentDocument content(Long id, String value) {
        CommentContentDocument content = new CommentContentDocument();
        content.setId(id);
        content.setUsername(value);
        content.setContent(value);
        content.setContentType("text/plain");
        return content;
    }
}
