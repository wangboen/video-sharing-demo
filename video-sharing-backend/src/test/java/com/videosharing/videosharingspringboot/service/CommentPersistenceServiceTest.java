package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.messaging.CommentCreateCommand;
import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import com.videosharing.videosharingspringboot.model.CommentContentDocument;
import com.videosharing.videosharingspringboot.model.CommentRelation;
import com.videosharing.videosharingspringboot.model.CommentStatus;
import com.videosharing.videosharingspringboot.repository.CommentCommandStatusRepository;
import com.videosharing.videosharingspringboot.repository.CommentContentRepository;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentPersistenceServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T09:00:00Z");

    @Mock
    private CommentContentRepository contentRepository;

    @Mock
    private CommentRelationRepository relationRepository;

    @Mock
    private CommentCommandStatusRepository commandStatusRepository;

    @InjectMocks
    private CommentPersistenceService service;

    @Test
    void persistCreateCommandWritesMongoThenMySqlAndCompletesCommand() {
        CommentCreateCommand command = command();
        CommentCommandStatus acceptedStatus = new CommentCommandStatus();
        acceptedStatus.setRequestId(command.requestId());
        acceptedStatus.setCommentId(command.commentId());
        acceptedStatus.setUserId(command.userId());
        acceptedStatus.setStatus("ACCEPTED");
        acceptedStatus.setCreatedAt(CREATED_AT);
        acceptedStatus.setUpdatedAt(CREATED_AT);
        when(relationRepository.existsById(command.commentId())).thenReturn(false);
        when(commandStatusRepository.findById(command.requestId())).thenReturn(Optional.of(acceptedStatus));

        service.persistCreateCommand(command);

        ArgumentCaptor<CommentContentDocument> contentCaptor = ArgumentCaptor.forClass(CommentContentDocument.class);
        ArgumentCaptor<CommentRelation> relationCaptor = ArgumentCaptor.forClass(CommentRelation.class);
        ArgumentCaptor<CommentCommandStatus> statusCaptor = ArgumentCaptor.forClass(CommentCommandStatus.class);
        InOrder order = inOrder(contentRepository, relationRepository, commandStatusRepository);
        order.verify(contentRepository).save(contentCaptor.capture());
        order.verify(relationRepository).save(relationCaptor.capture());
        order.verify(commandStatusRepository).save(statusCaptor.capture());

        CommentContentDocument content = contentCaptor.getValue();
        assertThat(content.getId()).isEqualTo(command.commentId());
        assertThat(content.getUsername()).isEqualTo(command.username());
        assertThat(content.getContent()).isEqualTo(command.content());
        assertThat(content.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(content.getUpdatedAt()).isEqualTo(CREATED_AT);

        CommentRelation relation = relationCaptor.getValue();
        assertThat(relation.getCommentId()).isEqualTo(command.commentId());
        assertThat(relation.getUserId()).isEqualTo(command.userId());
        assertThat(relation.getVideoId()).isEqualTo(command.videoId());
        assertThat(relation.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(relation.getLikeCount()).isZero();
        assertThat(relation.isAnonymized()).isFalse();
        assertThat(relation.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(relation.getUpdatedAt()).isEqualTo(CREATED_AT);

        CommentCommandStatus status = statusCaptor.getValue();
        assertThat(status.getStatus()).isEqualTo("COMPLETED");
        assertThat(status.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void persistCreateCommandSkipsDuplicateCommentId() {
        CommentCreateCommand command = command();
        when(relationRepository.existsById(command.commentId())).thenReturn(false, true);
        when(commandStatusRepository.findById(command.requestId())).thenReturn(Optional.empty());

        service.persistCreateCommand(command);
        service.persistCreateCommand(command);

        verify(contentRepository, times(1)).save(org.mockito.ArgumentMatchers.any(CommentContentDocument.class));
        verify(relationRepository, times(1)).save(org.mockito.ArgumentMatchers.any(CommentRelation.class));
        verify(commandStatusRepository, times(1)).save(org.mockito.ArgumentMatchers.any(CommentCommandStatus.class));
        verifyNoMoreInteractions(contentRepository, commandStatusRepository);
    }

    private CommentCreateCommand command() {
        return new CommentCreateCommand(
                "request-123",
                101L,
                202L,
                303L,
                "alice",
                "Useful comment",
                CREATED_AT);
    }
}
