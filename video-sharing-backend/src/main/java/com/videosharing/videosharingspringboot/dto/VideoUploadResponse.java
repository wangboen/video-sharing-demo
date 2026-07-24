package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

import java.time.LocalDateTime;

public record VideoUploadResponse(
        Long id,
        String title,
        String description,
        LocalDateTime uploadDate,
        VideoStatus status
) {
    public static VideoUploadResponse from(Video video) {
        return new VideoUploadResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUploadDate(), video.getStatus());
    }
}
