package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.CommentResponse;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserCommentService {
    private final CommentRelationRepository relations;
    private final CommentContentRepository contents;

    public UserCommentService(CommentRelationRepository relations, CommentContentRepository contents) {
        this.relations = relations;
        this.contents = contents;
    }

    public void anonymizeOwnComment(User user, Long commentId) {
        CommentRelation relation = relations.findById(commentId)
                .filter(comment -> comment.getUserId().equals(user.getId()))
                .orElseThrow(CommentNotFoundException::new);
        relation.setAnonymized(true);
        relation.setAnonymizedAt(Instant.now());
        relations.save(relation);
    }

    public Page<CommentResponse> listOwnComments(User user, Pageable pageable) {
        Page<CommentRelation> relationsPage = relations.findByUserIdAndAnonymizedFalse(user.getId(), pageable);
        Map<Long, CommentContentDocument> contentsById = new HashMap<>();
        contents.findAllById(relationsPage.stream().map(CommentRelation::getCommentId).toList())
                .forEach(content -> contentsById.put(content.getId(), content));
        return relationsPage.map(relation -> response(relation, contentsById.get(relation.getCommentId())));
    }

    private CommentResponse response(CommentRelation relation, CommentContentDocument content) {
        return new CommentResponse(relation.getCommentId(), content.getUsername(), content.getContent(), content.getContentType(),
                relation.getLikeCount(), false, false, relation.getCreatedAt());
    }

    public static class CommentNotFoundException extends RuntimeException {
    }
}
