package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.CommentCommandStatusResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentAcceptedResponse;
import com.videosharing.videosharingspringboot.dto.CreateCommentRequest;
import com.videosharing.videosharingspringboot.dto.VideoCommentsResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.CommentCommandService;
import com.videosharing.videosharingspringboot.service.CommentQueryService;
import com.videosharing.videosharingspringboot.service.UserCommentService;
import com.videosharing.videosharingspringboot.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {
    private final CommentCommandService commands;
    private final CommentQueryService queries;
    private final UserCommentService userComments;
    private final UserService users;

    public CommentController(CommentCommandService commands, CommentQueryService queries, UserCommentService userComments,
                             UserService users) {
        this.commands = commands;
        this.queries = queries;
        this.userComments = userComments;
        this.users = users;
    }

    @PostMapping("/api/videos/{videoId}/comments")
    public ResponseEntity<CreateCommentAcceptedResponse> createComment(@PathVariable Long videoId,
                                                                        @RequestBody CreateCommentRequest request,
                                                                        Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(commands.acceptCreateComment(currentUser(authentication), videoId, request));
    }

    @GetMapping("/api/videos/{videoId}/comments")
    public ResponseEntity<VideoCommentsResponse> getComments(@PathVariable Long videoId,
                                                              @RequestParam(required = false) String cursor,
                                                              @RequestParam(required = false) Integer size,
                                                              Authentication authentication) {
        Long userId = authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null : currentUser(authentication).getId();
        return ResponseEntity.ok(queries.getVideoComments(videoId, cursor, size, userId));
    }

    @GetMapping("/api/comments/commands/{requestId}")
    public ResponseEntity<CommentCommandStatusResponse> getCommandStatus(@PathVariable String requestId) {
        return queries.getCommandStatus(requestId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/users/me/comments")
    public Page<com.videosharing.videosharingspringboot.dto.CommentResponse> listOwnComments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return userComments.listOwnComments(currentUser(authentication),
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50))));
    }

    @DeleteMapping("/api/users/me/comments/{commentId}")
    public ResponseEntity<Void> anonymizeOwnComment(@PathVariable Long commentId, Authentication authentication) {
        try {
            userComments.anonymizeOwnComment(currentUser(authentication), commentId);
            return ResponseEntity.noContent().build();
        } catch (UserCommentService.CommentNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) throw new IllegalArgumentException("Authentication is required");
        return users.findByUsername(authentication.getName()).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
