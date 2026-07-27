package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;

public record ModerationVideoResponse(
        Long id,
        String title,
        String description,
        String uploader,
        LocalDateTime uploadDate,
        VideoStatus status,
        Long version,
        Instant claimedAt,
        Instant leaseExpiresAt,
        boolean hasThumbnail,
        String videoUrl,
        String thumbnailUrl
) {
    public static ModerationVideoResponse from(Video video) {
        return from(video, null, null);
    }

    public static ModerationVideoResponse from(Video video, String objectStoragePublicBaseUrl) {
        return from(video, objectUrl(objectStoragePublicBaseUrl, video.getFilePath()),
                video.getThumbnailPath() == null ? null : objectUrl(objectStoragePublicBaseUrl, video.getThumbnailPath()));
    }

    public static ModerationVideoResponse from(Video video, String videoUrl, String thumbnailUrl) {
        return new ModerationVideoResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUser().getUsername(), video.getUploadDate(), video.getStatus(), video.getVersion(),
                video.getClaimedAt(), video.getLeaseExpiresAt(), video.getThumbnailPath() != null,
                videoUrl, thumbnailUrl);
    }

    private static String objectUrl(String baseUrl, String key) {
        String trimmed = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return trimmed + "/" + UriUtils.encodePath(key, StandardCharsets.UTF_8);
    }
}
