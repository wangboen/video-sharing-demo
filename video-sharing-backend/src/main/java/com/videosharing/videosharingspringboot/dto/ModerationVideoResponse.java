package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

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
        boolean hasThumbnail
) {
    public static ModerationVideoResponse from(Video video) {
        return new ModerationVideoResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUser().getUsername(), video.getUploadDate(), video.getStatus(), video.getVersion(),
                video.getClaimedAt(), video.getLeaseExpiresAt(), video.getThumbnailPath() != null);
    }
}
