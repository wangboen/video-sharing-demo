package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {
    @Mock private CommentRelationRepository relations;
    @Mock private CommentContentRepository contents;
    @Mock private CommentLikeRepository likes;
    @Mock private HotCommentCacheService hotCache;

    @Test
    void composesFeaturedAndNormalCommentsInMysqlOrder() {
        CommentQueryService service = service();
        CommentRelation featured = relation(10L, 100L, false, "2026-07-31T12:00:00Z");
        CommentRelation duplicateFeatured = relation(10L, 100L, false, "2026-07-31T12:00:00Z");
        CommentRelation newestNormal = relation(30L, 5L, false, "2026-07-31T11:00:00Z");
        CommentRelation olderNormal = relation(20L, 3L, false, "2026-07-31T10:00:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of(featured));
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class)))
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
        CommentQueryService service = service();
        CommentRelation anonymous = relation(10L, 3L, true, "2026-07-31T12:00:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class))).thenReturn(List.of(anonymous));
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

    @Test
    void fillsNormalPageAfterSkippingFeaturedRowsInTimeIndex() {
        CommentQueryService service = service();
        CommentRelation featuredOne = relation(10L, 100L, false, "2026-07-31T12:03:00Z");
        CommentRelation featuredTwo = relation(11L, 90L, false, "2026-07-31T12:02:00Z");
        CommentRelation firstNormal = relation(20L, 5L, false, "2026-07-31T12:01:00Z");
        CommentRelation secondNormal = relation(21L, 4L, false, "2026-07-31T12:00:00Z");
        CommentRelation remainingNormal = relation(22L, 3L, false, "2026-07-31T11:59:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(featuredOne, featuredTwo));
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(featuredOne, featuredTwo, firstNormal));
        when(relations.findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(secondNormal, remainingNormal));
        when(contents.findAllById(any())).thenReturn(List.of(content(10L, "featured-one"), content(11L, "featured-two"),
                content(20L, "first"), content(21L, "second")));

        var response = service.getVideoComments(42L, null, 2, null);

        assertThat(response.comments()).extracting(comment -> comment.commentId()).containsExactly(20L, 21L);
        assertThat(response.nextCursor()).isEqualTo("2026-07-31T12:00:00Z|21");
        verify(relations).findVisibleByVideo(eq(42L), any(Pageable.class));
        verify(relations).findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class));
    }

    @Test
    void omitsNextCursorWhenOnlyFeaturedRowsRemain() {
        CommentQueryService service = service();
        CommentRelation normal = relation(20L, 5L, false, "2026-07-31T12:01:00Z");
        CommentRelation featured = relation(10L, 100L, false, "2026-07-31T12:00:00Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of(featured));
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(normal, featured));
        when(relations.findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(contents.findAllById(any())).thenReturn(List.of(content(10L, "featured"), content(20L, "normal")));

        var response = service.getVideoComments(42L, null, 1, null);

        assertThat(response.comments()).extracting(comment -> comment.commentId()).containsExactly(20L);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void preservesNanosecondPrecisionWhenAdvancingCursor() {
        CommentQueryService service = service();
        Instant preciseTime = Instant.parse("2026-07-31T12:00:00.123456789Z");
        CommentRelation first = relation(20L, 5L, false, preciseTime.toString());
        CommentRelation second = relation(19L, 4L, false, "2026-07-31T12:00:00.123456788Z");
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class))).thenReturn(List.of(first, second));
        when(relations.findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(contents.findAllById(List.of(20L))).thenReturn(List.of(content(20L, "first")));

        var firstPage = service.getVideoComments(42L, null, 1, null);
        service.getVideoComments(42L, firstPage.nextCursor(), 1, null);

        org.mockito.ArgumentCaptor<Instant> cursors = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(relations).findVisibleByVideoAfterCursor(eq(42L), cursors.capture(), any(), any(Pageable.class));
        assertThat(cursors.getValue()).isEqualTo(preciseTime);
    }

    @Test
    void usesDedicatedInitialPageQueryWithoutNullCursorParameters() {
        CommentQueryService service = service();
        when(hotCache.getHotCommentIds(42L, 3, 10L)).thenReturn(List.of());
        when(relations.findTopHotVisible(eq(42L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(relations.findVisibleByVideo(eq(42L), any(Pageable.class))).thenReturn(List.of());

        service.getVideoComments(42L, null, 20, null);

        verify(relations).findVisibleByVideo(eq(42L), any(Pageable.class));
        org.mockito.Mockito.verify(relations, org.mockito.Mockito.never())
                .findVisibleByVideoAfterCursor(eq(42L), any(), any(), any(Pageable.class));
    }

    private CommentQueryService service() {
        return new CommentQueryService(relations, contents, likes, hotCache, properties());
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
