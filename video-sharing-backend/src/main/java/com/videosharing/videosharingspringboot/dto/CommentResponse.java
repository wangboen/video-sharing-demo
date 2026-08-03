package com.videosharing.videosharingspringboot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;

public record CommentResponse(@JsonSerialize(using = ToStringSerializer.class) Long commentId,
                              String username, String content, String contentType,
                              Long likeCount, boolean likedByMe, boolean anonymized, Instant createdAt) {
}
