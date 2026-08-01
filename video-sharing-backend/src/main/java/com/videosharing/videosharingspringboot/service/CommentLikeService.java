package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.CommentLikeId;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.hibernate.exception.ConstraintViolationException;
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
        int inserted;
        try {
            inserted = likes.insertIfAbsent(commentId, user.getId(), Instant.now());
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateLikeViolation(exception)) {
                return;
            }
            throw exception;
        }
        if (inserted > 0 && relations.incrementVisibleLikeCount(commentId) > 0) {
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

    private boolean isDuplicateLikeViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return "PRIMARY".equalsIgnoreCase(constraintViolation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    public static class CommentNotFoundException extends RuntimeException {
    }
}
