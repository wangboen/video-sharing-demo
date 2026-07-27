package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;

public record UserSubmissionResponse(
        Long id,
        String title,
        String description,
        LocalDateTime uploadDate,
        VideoStatus status,
        Instant reviewedAt,
        String rejectionReason,
        boolean canDownloadVideo,
        boolean canDownloadThumbnail,
        Instant downloadAvailableUntil,
        Instant videoDeletedAt,
        Instant thumbnailDeletedAt
) {
    public static UserSubmissionResponse from(Video video, Instant now, Duration rejectedRetention) {
        Instant downloadAvailableUntil = rejectedDownloadAvailableUntil(video, rejectedRetention);
        boolean statusDownloadable = switch (video.getStatus()) {
            case PENDING, APPROVED -> true;
            case REJECTED -> downloadAvailableUntil != null && now.isBefore(downloadAvailableUntil);
        };
        return new UserSubmissionResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUploadDate(), video.getStatus(), video.getReviewedAt(), video.getRejectionReason(),
                statusDownloadable && video.getFilePath() != null && video.getFileDeletedAt() == null,
                statusDownloadable && video.getThumbnailPath() != null && video.getThumbnailDeletedAt() == null,
                downloadAvailableUntil, video.getFileDeletedAt(), video.getThumbnailDeletedAt());
    }

    private static Instant rejectedDownloadAvailableUntil(Video video, Duration rejectedRetention) {
        if (video.getStatus() != VideoStatus.REJECTED || video.getReviewedAt() == null) {
            return null;
        }
        return video.getReviewedAt().plus(rejectedRetention);
    }
}
