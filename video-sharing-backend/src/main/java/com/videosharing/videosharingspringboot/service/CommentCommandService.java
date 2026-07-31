package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.dto.CreateCommentAcceptedResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentRequest;
import com.videosharing.videosharingspringboot.messaging.CommentCommandPublisher;
import com.videosharing.videosharingspringboot.messaging.CommentCreateCommand;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import com.videosharing.videosharingspringboot.repository.CommentCommandStatusRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class CommentCommandService {
    private final VideoRepository videoRepository;
    private final CommentCommandPublisher publisher;
    private final CommentCommandStatusRepository commandStatusRepository;
    private final SnowflakeIdGenerator snowflake;
    private final CommentProperties properties;

    public CommentCommandService(VideoRepository videoRepository,
                                 CommentCommandPublisher publisher,
                                 CommentCommandStatusRepository commandStatusRepository,
                                 SnowflakeIdGenerator snowflake,
                                 CommentProperties properties) {
        this.videoRepository = videoRepository;
        this.publisher = publisher;
        this.commandStatusRepository = commandStatusRepository;
        this.snowflake = snowflake;
        this.properties = properties;
    }

    public CreateCommentAcceptedResponse acceptCreateComment(User user, Long videoId, CreateCommentRequest request) {
        String content = request == null ? null : request.content();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment content must not be blank");
        }
        if (content.length() > properties.getMaxLength()) {
            throw new IllegalArgumentException("Comment content exceeds the maximum length");
        }
        if (user == null || user.getRole() != Role.USER) {
            throw new AccessDeniedException("ROLE_USER is required to create comments");
        }

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));
        if (video.getStatus() != VideoStatus.APPROVED) {
            throw new IllegalArgumentException("Comments can only be created for approved videos");
        }

        String requestId = UUID.randomUUID().toString();
        long commentId = snowflake.nextId();
        Instant createdAt = Instant.now();
        publisher.publish(new CommentCreateCommand(
                requestId, commentId, user.getId(), videoId, user.getUsername(), content, createdAt));
        CommentCommandStatus commandStatus = new CommentCommandStatus();
        commandStatus.setRequestId(requestId);
        commandStatus.setCommentId(commentId);
        commandStatus.setUserId(user.getId());
        commandStatus.setStatus("ACCEPTED");
        commandStatus.setCreatedAt(createdAt);
        commandStatus.setUpdatedAt(createdAt);
        commandStatusRepository.save(commandStatus);
        return new CreateCommentAcceptedResponse(requestId, commentId, "ACCEPTED");
    }
}
