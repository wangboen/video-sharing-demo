package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentLikeServiceTest {
    @Mock private CommentRelationRepository relations;
    @Mock private CommentLikeRepository likes;
    @Mock private HotCommentCacheService hotCache;

    @Test
    void likeIncrementsOnceAndMarksVideoForRefresh() {
        CommentLikeService service = new CommentLikeService(relations, likes, hotCache);
        CommentRelation relation = visibleRelation(101L, 42L, 5L);
        when(relations.findById(101L)).thenReturn(Optional.of(relation));
        when(likes.existsById(likeId(101L, 7L))).thenReturn(false);

        service.like(user(7L), 101L);

        assertThat(relation.getLikeCount()).isEqualTo(6L);
        verify(relations).save(relation);
        verify(hotCache).markVideoForHotRefresh(42L);
        ArgumentCaptor<CommentLike> likeCaptor = ArgumentCaptor.forClass(CommentLike.class);
        verify(likes).save(likeCaptor.capture());
        assertThat(likeCaptor.getValue().getId()).isEqualTo(likeId(101L, 7L));
    }

    @Test
    void duplicateLikeDoesNotIncrementOrMarkRefresh() {
        CommentLikeService service = new CommentLikeService(relations, likes, hotCache);
        CommentRelation relation = visibleRelation(101L, 42L, 5L);
        when(relations.findById(101L)).thenReturn(Optional.of(relation));
        when(likes.existsById(likeId(101L, 7L))).thenReturn(true);

        service.like(user(7L), 101L);

        assertThat(relation.getLikeCount()).isEqualTo(5L);
        verify(relations, never()).save(relation);
        verify(likes, never()).save(org.mockito.ArgumentMatchers.any(CommentLike.class));
        verifyNoInteractions(hotCache);
    }

    @Test
    void unlikeDecrementsOnceAndMarksVideoForRefresh() {
        CommentLikeService service = new CommentLikeService(relations, likes, hotCache);
        CommentRelation relation = visibleRelation(101L, 42L, 5L);
        when(relations.findById(101L)).thenReturn(Optional.of(relation));
        when(likes.existsById(likeId(101L, 7L))).thenReturn(true);

        service.unlike(user(7L), 101L);

        assertThat(relation.getLikeCount()).isEqualTo(4L);
        verify(likes).deleteById(likeId(101L, 7L));
        verify(relations).save(relation);
        verify(hotCache).markVideoForHotRefresh(42L);
    }

    @Test
    void duplicateUnlikeDoesNotDecrementOrMarkRefresh() {
        CommentLikeService service = new CommentLikeService(relations, likes, hotCache);
        CommentRelation relation = visibleRelation(101L, 42L, 5L);
        when(relations.findById(101L)).thenReturn(Optional.of(relation));
        when(likes.existsById(likeId(101L, 7L))).thenReturn(false);

        service.unlike(user(7L), 101L);

        assertThat(relation.getLikeCount()).isEqualTo(5L);
        verify(likes, never()).deleteById(likeId(101L, 7L));
        verify(relations, never()).save(relation);
        verifyNoInteractions(hotCache);
    }

    @Test
    void hiddenCommentsCannotBeLiked() {
        CommentLikeService service = new CommentLikeService(relations, likes, hotCache);
        CommentRelation relation = visibleRelation(101L, 42L, 5L);
        relation.setStatus(CommentStatus.HIDDEN);
        when(relations.findById(101L)).thenReturn(Optional.of(relation));

        assertThatThrownBy(() -> service.like(user(7L), 101L))
                .isInstanceOf(CommentLikeService.CommentNotFoundException.class);
    }

    private CommentRelation visibleRelation(Long commentId, Long videoId, Long likeCount) {
        CommentRelation relation = new CommentRelation();
        relation.setCommentId(commentId);
        relation.setVideoId(videoId);
        relation.setLikeCount(likeCount);
        relation.setStatus(CommentStatus.VISIBLE);
        return relation;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        return user;
    }

    private CommentLikeId likeId(Long commentId, Long userId) {
        CommentLikeId id = new CommentLikeId();
        id.setCommentId(commentId);
        id.setUserId(userId);
        return id;
    }
}
