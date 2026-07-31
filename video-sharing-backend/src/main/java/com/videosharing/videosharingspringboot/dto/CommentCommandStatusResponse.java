package com.videosharing.videosharingspringboot.dto;

public record CommentCommandStatusResponse(String requestId, Long commentId, String status, String failureReason) {
}
