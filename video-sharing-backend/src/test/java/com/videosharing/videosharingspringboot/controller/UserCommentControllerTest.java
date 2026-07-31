package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.CommentResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.CommentCommandService;
import com.videosharing.videosharingspringboot.service.CommentQueryService;
import com.videosharing.videosharingspringboot.service.UserCommentService;
import com.videosharing.videosharingspringboot.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommentControllerTest {
    @Mock private CommentCommandService commands;
    @Mock private CommentQueryService queries;
    @Mock private UserCommentService userComments;
    @Mock private UserService users;
    @Mock private Authentication authentication;

    @Test
    void listsCurrentUsersCommentsWithBoundedPagination() {
        CommentController controller = controller();
        User user = user();
        var page = new PageImpl<>(List.of(new CommentResponse(10L, "alice", "hello", "text/plain", 3L,
                false, false, Instant.parse("2026-07-31T12:00:00Z"))), PageRequest.of(0, 50), 1);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userComments.listOwnComments(user, PageRequest.of(0, 50))).thenReturn(page);

        var response = controller.listOwnComments(-1, 100, authentication);

        assertThat(response).isSameAs(page);
        assertThat(response.getContent().get(0).username()).isEqualTo("alice");
        assertThat(response.getContent().get(0).getClass().getRecordComponents())
                .extracting(component -> component.getName()).doesNotContain("userId");
        verify(userComments).listOwnComments(user, PageRequest.of(0, 50));
    }

    @Test
    void anonymizesCurrentUsersComment() {
        CommentController controller = controller();
        User user = user();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        var response = controller.anonymizeOwnComment(10L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userComments).anonymizeOwnComment(user, 10L);
    }

    @Test
    void returnsNotFoundWhenCommentIsNotOwnedByCurrentUser() {
        CommentController controller = controller();
        User user = user();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new UserCommentService.CommentNotFoundException())
                .when(userComments).anonymizeOwnComment(user, 10L);

        var response = controller.anonymizeOwnComment(10L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private CommentController controller() {
        when(authentication.getName()).thenReturn("alice");
        return new CommentController(commands, queries, userComments, users);
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        return user;
    }
}
