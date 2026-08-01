package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CommentLikeService {
    private final CommentRelationRepository relations;
    private final CommentLikeRepository likes;
    private final HotCommentCacheService hotCache;

    public CommentLikeService(CommentRelationRepository relations, CommentLikeRepository likes,
                              HotCommentCacheService hotCache) {
        this.relations = relations;
        this.likes = likes;
        this.hotCache = hotCache;
    }

    @Transactional
    public void like(User user, Long commentId) {
        CommentRelation relation = visibleRelation(commentId);
        CommentLikeId id = likeId(commentId, user.getId());

        CommentLike like = new CommentLike();
        like.setId(id);
        like.setCreatedAt(Instant.now());
        try {
            likes.saveAndFlush(like);
        } catch (DataIntegrityViolationException exception) {
            return;
        }
        if (relations.incrementVisibleLikeCount(commentId) > 0) {
            hotCache.markVideoForHotRefresh(relation.getVideoId());
        }
    }

    @Transactional
    public void unlike(User user, Long commentId) {
        CommentRelation relation = visibleRelation(commentId);
        CommentLikeId id = likeId(commentId, user.getId());

        if (likes.deleteExisting(id) > 0 && relations.decrementVisibleLikeCount(commentId) > 0) {
            hotCache.markVideoForHotRefresh(relation.getVideoId());
        }
    }

    private CommentRelation visibleRelation(Long commentId) {
        return relations.findById(commentId)
                .filter(relation -> relation.getStatus() == CommentStatus.VISIBLE)
                .orElseThrow(CommentNotFoundException::new);
    }

    private CommentLikeId likeId(Long commentId, Long userId) {
        CommentLikeId id = new CommentLikeId();
        id.setCommentId(commentId);
        id.setUserId(userId);
        return id;
    }

    public static class CommentNotFoundException extends RuntimeException {
    }
}
