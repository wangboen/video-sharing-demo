package com.videosharing.videosharingspringboot.dto;

import java.util.List;

public record CompleteUploadRequest(
        String uploadSessionId,
        String videoKey,
        String thumbnailKey,
        String title,
        String description,
        Long categoryId,
        List<Long> tagIds
) {
    public CompleteUploadRequest(String uploadSessionId, String videoKey, String thumbnailKey,
                                 String title, String description) {
        this(uploadSessionId, videoKey, thumbnailKey, title, description, null, List.of());
    }
}
