package com.videosharing.videosharingspringboot.dto;

public record UploadTargetResponse(
        String key,
        String uploadUrl,
        String contentType
) {
}
