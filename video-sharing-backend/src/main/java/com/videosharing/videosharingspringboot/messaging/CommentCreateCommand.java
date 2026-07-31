package com.videosharing.videosharingspringboot.messaging;

import java.time.Instant;

public record CommentCreateCommand(
        String requestId,
        Long commentId,
        Long userId,
        Long videoId,
        String username,
        String content,
        Instant createdAt
) {
}
