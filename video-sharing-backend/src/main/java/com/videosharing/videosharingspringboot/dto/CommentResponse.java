package com.videosharing.videosharingspringboot.dto;

import java.time.Instant;

public record CommentResponse(Long commentId, String username, String content, String contentType,
                              Long likeCount, boolean likedByMe, boolean anonymized, Instant createdAt) {
}
