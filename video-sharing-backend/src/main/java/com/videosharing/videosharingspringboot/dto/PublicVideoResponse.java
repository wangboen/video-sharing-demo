package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public record PublicVideoResponse(
        Long id,
        String title,
        String description,
        String uploader,
        String videoUrl,
        String thumbnailUrl,
        LocalDateTime uploadDate,
        Long views
) {
    public static PublicVideoResponse from(Video video) {
        if (video.getStatus() != VideoStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved videos may have public media URLs");
        }
        return new PublicVideoResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUser().getUsername(), publicUrl("/uploads/", video.getFilePath()),
                video.getThumbnailPath() == null ? null : publicUrl("/thumbnails/", video.getThumbnailPath()),
                video.getUploadDate(), video.getViews());
    }

    private static String publicUrl(String prefix, String fileName) {
        return prefix + UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);
    }
}
