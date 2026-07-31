package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.dto.CommentCommandStatusResponse;
import com.videosharing.videosharingspringboot.dto.CommentResponse;
import com.videosharing.videosharingspringboot.dto.VideoCommentsResponse;
import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.repository.CommentCommandStatusRepository;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentLikeRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CommentQueryService {
    private static final String ANONYMOUS_USERNAME = "匿名用户";

    private final CommentRelationRepository relations;
    private final CommentContentRepository contents;
    private final CommentLikeRepository likes;
    private final HotCommentCacheService hotCache;
    private final CommentProperties properties;
    private final CommentCommandStatusRepository commandStatuses;

    @Autowired
    public CommentQueryService(CommentRelationRepository relations, CommentContentRepository contents,
                               CommentLikeRepository likes, HotCommentCacheService hotCache,
                               CommentProperties properties, CommentCommandStatusRepository commandStatuses) {
        this.relations = relations;
        this.contents = contents;
        this.likes = likes;
        this.hotCache = hotCache;
        this.properties = properties;
        this.commandStatuses = commandStatuses;
    }

    CommentQueryService(CommentRelationRepository relations, CommentContentRepository contents,
                        CommentLikeRepository likes, HotCommentCacheService hotCache, CommentProperties properties) {
        this(relations, contents, likes, hotCache, properties, null);
    }

    public VideoCommentsResponse getVideoComments(Long videoId, String cursor, Integer requestedSize, Long userId) {
        int pageSize = normalizeSize(requestedSize);
        List<CommentRelation> featured = featuredRelations(videoId);
        Set<Long> featuredIds = featured.stream().map(CommentRelation::getCommentId).collect(java.util.stream.Collectors.toSet());
        Cursor before = Cursor.parse(cursor);
        List<CommentRelation> indexed = relations.findVisibleByVideoAfterCursor(videoId, before.createdAt(), before.commentId(),
                PageRequest.of(0, pageSize + 1));
        boolean hasNext = indexed.size() > pageSize;
        List<CommentRelation> normal = indexed.stream().limit(pageSize)
                .filter(comment -> !featuredIds.contains(comment.getCommentId())).toList();
        List<CommentRelation> all = combine(featured, normal);
        Map<Long, CommentContentDocument> contentById = contentById(all);
        Set<Long> likedIds = likedCommentIds(all, userId);
        String nextCursor = hasNext ? formatCursor(indexed.get(pageSize - 1)) : null;
        return new VideoCommentsResponse(responses(featured, contentById, likedIds), responses(normal, contentById, likedIds), nextCursor);
    }

    public Optional<CommentCommandStatusResponse> getCommandStatus(String requestId) {
        return commandStatuses.findById(requestId).map(this::statusResponse);
    }

    private List<CommentRelation> featuredRelations(Long videoId) {
        List<Long> cachedIds = hotCache.getHotCommentIds(videoId, properties.getFeaturedLimit(), properties.getFeaturedMinLikes());
        if (cachedIds.isEmpty()) {
            return relations.findTopHotVisible(videoId, properties.getFeaturedMinLikes(), PageRequest.of(0, properties.getFeaturedLimit()));
        }
        Map<Long, CommentRelation> relationById = new HashMap<>();
        relations.findAllById(cachedIds).forEach(relation -> relationById.put(relation.getCommentId(), relation));
        return cachedIds.stream().map(relationById::get).filter(java.util.Objects::nonNull).toList();
    }

    private Map<Long, CommentContentDocument> contentById(Collection<CommentRelation> comments) {
        Map<Long, CommentContentDocument> result = new HashMap<>();
        contents.findAllById(comments.stream().map(CommentRelation::getCommentId).distinct().toList())
                .forEach(content -> result.put(content.getId(), content));
        return result;
    }

    private Set<Long> likedCommentIds(Collection<CommentRelation> comments, Long userId) {
        if (userId == null) return Set.of();
        List<CommentLikeId> ids = comments.stream().map(CommentRelation::getCommentId).distinct()
                .map(commentId -> likeId(commentId, userId)).toList();
        Set<Long> result = new HashSet<>();
        likes.findAllById(ids).forEach(like -> result.add(like.getId().getCommentId()));
        return result;
    }

    private List<CommentResponse> responses(List<CommentRelation> comments, Map<Long, CommentContentDocument> contentById,
                                            Set<Long> likedIds) {
        return comments.stream().map(relation -> response(relation, contentById.get(relation.getCommentId()), likedIds)).toList();
    }

    private CommentResponse response(CommentRelation relation, CommentContentDocument content, Set<Long> likedIds) {
        return new CommentResponse(relation.getCommentId(), relation.isAnonymized() ? ANONYMOUS_USERNAME : content.getUsername(),
                content.getContent(), content.getContentType(), relation.getLikeCount(), likedIds.contains(relation.getCommentId()),
                relation.isAnonymized(), relation.getCreatedAt());
    }

    private List<CommentRelation> combine(List<CommentRelation> first, List<CommentRelation> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private int normalizeSize(Integer size) {
        return Math.max(1, Math.min(size == null ? properties.getPageSizeDefault() : size, properties.getPageSizeMax()));
    }

    private String formatCursor(CommentRelation relation) {
        return relation.getCreatedAt().toEpochMilli() + ":" + relation.getCommentId();
    }

    private CommentCommandStatusResponse statusResponse(CommentCommandStatus status) {
        return new CommentCommandStatusResponse(status.getRequestId(), status.getCommentId(), status.getStatus(), status.getFailureReason());
    }

    private CommentLikeId likeId(Long commentId, Long userId) {
        CommentLikeId id = new CommentLikeId();
        id.setCommentId(commentId);
        id.setUserId(userId);
        return id;
    }

    private record Cursor(Instant createdAt, Long commentId) {
        private static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(Instant.MAX, Long.MAX_VALUE);
            String[] parts = value.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid comment cursor");
            try {
                return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid comment cursor");
            }
        }
    }
}
