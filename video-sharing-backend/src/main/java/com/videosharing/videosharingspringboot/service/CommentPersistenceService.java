package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.messaging.CommentCreateCommand;
import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.repository.CommentCommandStatusRepository;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CommentPersistenceService {
    private final CommentContentRepository contentRepository;
    private final CommentRelationRepository relationRepository;
    private final CommentCommandStatusRepository commandStatusRepository;

    public CommentPersistenceService(CommentContentRepository contentRepository,
                                     CommentRelationRepository relationRepository,
                                     CommentCommandStatusRepository commandStatusRepository) {
        this.contentRepository = contentRepository;
        this.relationRepository = relationRepository;
        this.commandStatusRepository = commandStatusRepository;
    }

    public void persistCreateCommand(CommentCreateCommand command) {
        if (relationRepository.existsById(command.commentId())) {
            commandStatusRepository.save(completedStatus(command));
            return;
        }

        contentRepository.save(content(command));
        relationRepository.save(relation(command));
        commandStatusRepository.save(completedStatus(command));
    }

    private CommentContentDocument content(CommentCreateCommand command) {
        CommentContentDocument content = new CommentContentDocument();
        content.setId(command.commentId());
        content.setUsername(command.username());
        content.setContent(command.content());
        content.setCreatedAt(command.createdAt());
        content.setUpdatedAt(command.createdAt());
        return content;
    }

    private CommentRelation relation(CommentCreateCommand command) {
        CommentRelation relation = new CommentRelation();
        relation.setCommentId(command.commentId());
        relation.setVideoId(command.videoId());
        relation.setUserId(command.userId());
        relation.setStatus(CommentStatus.VISIBLE);
        relation.setLikeCount(0L);
        relation.setAnonymized(false);
        relation.setCreatedAt(command.createdAt());
        relation.setUpdatedAt(command.createdAt());
        return relation;
    }

    private CommentCommandStatus completedStatus(CommentCreateCommand command) {
        Optional<CommentCommandStatus> existing = commandStatusRepository.findById(command.requestId());
        CommentCommandStatus status = existing.orElseGet(CommentCommandStatus::new);
        status.setRequestId(command.requestId());
        status.setCommentId(command.commentId());
        status.setUserId(command.userId());
        status.setStatus("COMPLETED");
        status.setFailureReason(null);
        status.setCreatedAt(existing.map(CommentCommandStatus::getCreatedAt).orElse(command.createdAt()));
        status.setUpdatedAt(command.createdAt());
        return status;
    }
}
