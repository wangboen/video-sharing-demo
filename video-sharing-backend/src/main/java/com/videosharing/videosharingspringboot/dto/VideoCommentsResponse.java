package com.videosharing.videosharingspringboot.dto;

import java.util.List;

public record VideoCommentsResponse(List<CommentResponse> featured, List<CommentResponse> comments, String nextCursor) {
}
