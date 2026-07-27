package com.videosharing.videosharingspringboot.dto;

public record CompleteUploadRequest(
        String uploadSessionId,
        String videoKey,
        String thumbnailKey,
        String title,
        String description
) {
}
