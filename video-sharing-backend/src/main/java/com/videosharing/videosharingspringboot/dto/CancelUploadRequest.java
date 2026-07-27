package com.videosharing.videosharingspringboot.dto;

public record CancelUploadRequest(
        String uploadSessionId,
        String videoKey,
        String thumbnailKey
) {
}
