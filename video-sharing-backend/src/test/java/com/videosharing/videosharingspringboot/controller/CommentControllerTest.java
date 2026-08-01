package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.CommentCommandStatusResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentAcceptedResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentRequest;
import com.videosharing.videosharingspringboot.dto.VideoCommentsResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.CommentCommandService;
import com.videosharing.videosharingspringboot.service.CommentLikeService;
import com.videosharing.videosharingspringboot.service.CommentQueryService;
import com.videosharing.videosharingspringboot.service.UserCommentService;
import com.videosharing.videosharingspringboot.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {
    @Mock private CommentCommandService commands;
    @Mock private CommentQueryService queries;
    @Mock private CommentLikeService likes;
    @Mock private UserCommentService userComments;
    @Mock private UserService users;

    @Test
    void publishesCommentAsAcceptedForAuthenticatedUser() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        User user = new User();
        user.setId(7L);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(commands.acceptCreateComment(user, 42L, new CreateCommentRequest("hello")))
                .thenReturn(new CreateCommentAcceptedResponse("request-1", 99L, "ACCEPTED"));

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");
        var response = controller.createComment(42L, new CreateCommentRequest("hello"), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().commentId()).isEqualTo(99L);
    }

    @Test
    void returnsPublicCommentsWithoutAUser() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        VideoCommentsResponse page = new VideoCommentsResponse(List.of(), List.of(), null);
        when(queries.getVideoComments(42L, null, 20, null)).thenReturn(page);

        var response = controller.getComments(42L, null, 20, null);

        assertThat(response.getBody()).isSameAs(page);
        verify(queries).getVideoComments(42L, null, 20, null);
    }

    @Test
    void treatsAnonymousAuthenticationAsAPublicCommentRequest() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        VideoCommentsResponse page = new VideoCommentsResponse(List.of(), List.of(), null);
        when(queries.getVideoComments(42L, null, 20, null)).thenReturn(page);
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        var response = controller.getComments(42L, null, 20, anonymous);

        assertThat(response.getBody()).isSameAs(page);
        verify(queries).getVideoComments(42L, null, 20, null);
    }

    @Test
    void returnsCommandStatusWhenPresent() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        when(queries.getCommandStatus("request-1"))
                .thenReturn(Optional.of(new CommentCommandStatusResponse("request-1", 99L, "ACCEPTED", null)));

        var response = controller.getCommandStatus("request-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("ACCEPTED");
    }

    @Test
    void likesCommentAsAuthenticatedUser() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        User user = new User();
        user.setId(7L);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");

        var response = controller.likeComment(101L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(likes).like(user, 101L);
    }

    @Test
    void unlikesCommentAsAuthenticatedUser() {
        CommentController controller = new CommentController(commands, queries, likes, userComments, users);
        User user = new User();
        user.setId(7L);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");

        var response = controller.unlikeComment(101L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(likes).unlike(user, 101L);
    }
}
