package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.dto.CreateCommentAcceptedResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentRequest;
import com.videosharing.videosharingspringboot.messaging.CommentCommandPublisher;
import com.videosharing.videosharingspringboot.messaging.CommentCreateCommand;
import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.CommentCommandStatusRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCommandServiceTest {
    @Mock
    private VideoRepository videos;
    @Mock
    private CommentCommandPublisher publisher;
    @Mock
    private CommentCommandStatusRepository commandStatuses;
    @Mock
    private SnowflakeIdGenerator snowflake;
    @Mock
    private CommentProperties properties;
    @InjectMocks
    private CommentCommandService service;

    @Test
    void acceptsValidCommentForApprovedVideoAndPublishesOneCommand() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setRole(Role.USER);
        Video video = new Video();
        video.setStatus(VideoStatus.APPROVED);
        when(videos.findById(42L)).thenReturn(Optional.of(video));
        when(properties.getMaxLength()).thenReturn(1_000);
        when(snowflake.nextId()).thenReturn(999L);

        CreateCommentAcceptedResponse response = service.acceptCreateComment(
                user, 42L, new CreateCommentRequest("A useful comment"));

        ArgumentCaptor<CommentCreateCommand> command = ArgumentCaptor.forClass(CommentCreateCommand.class);
        ArgumentCaptor<CommentCommandStatus> status = ArgumentCaptor.forClass(CommentCommandStatus.class);
        verify(publisher).publish(command.capture());
        verify(commandStatuses).save(status.capture());
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.commentId()).isEqualTo(999L);
        assertThat(response.requestId()).isNotBlank();
        assertThat(command.getValue().requestId()).isEqualTo(response.requestId());
        assertThat(command.getValue().commentId()).isEqualTo(999L);
        assertThat(command.getValue().videoId()).isEqualTo(42L);
        assertThat(command.getValue().userId()).isEqualTo(7L);
        assertThat(command.getValue().username()).isEqualTo("alice");
        assertThat(command.getValue().content()).isEqualTo("A useful comment");
        assertThat(command.getValue().createdAt()).isNotNull();
        assertThat(status.getValue().getRequestId()).isEqualTo(response.requestId());
        assertThat(status.getValue().getCommentId()).isEqualTo(999L);
        assertThat(status.getValue().getUserId()).isEqualTo(7L);
        assertThat(status.getValue().getStatus()).isEqualTo("ACCEPTED");
        assertThat(status.getValue().getCreatedAt()).isNotNull();
        assertThat(status.getValue().getUpdatedAt()).isEqualTo(status.getValue().getCreatedAt());
    }

    @Test
    void rejectsBlankContentWithoutPublishing() {
        assertThatThrownBy(() -> service.acceptCreateComment(user(), 42L, new CreateCommentRequest("  ")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsOversizedContentWithoutPublishing() {
        when(properties.getMaxLength()).thenReturn(3);

        assertThatThrownBy(() -> service.acceptCreateComment(user(), 42L, new CreateCommentRequest("long")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsMissingVideoWithoutPublishing() {
        when(properties.getMaxLength()).thenReturn(1_000);
        when(videos.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptCreateComment(user(), 42L, new CreateCommentRequest("comment")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsUnapprovedVideoWithoutPublishing() {
        Video video = new Video();
        video.setStatus(VideoStatus.PENDING);
        when(properties.getMaxLength()).thenReturn(1_000);
        when(videos.findById(42L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> service.acceptCreateComment(user(), 42L, new CreateCommentRequest("comment")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsNonUserRoleWithoutPublishing() {
        User reviewer = user();
        reviewer.setRole(Role.REVIEWER);
        when(properties.getMaxLength()).thenReturn(1_000);

        assertThatThrownBy(() -> service.acceptCreateComment(reviewer, 42L, new CreateCommentRequest("comment")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(publisher);
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setRole(Role.USER);
        return user;
    }
}
