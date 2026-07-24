package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public record UserSubmissionResponse(
        Long id,
        String title,
        String description,
        LocalDateTime uploadDate,
        VideoStatus status,
        Instant reviewedAt,
        String rejectionReason
) {
    public static UserSubmissionResponse from(Video video) {
        return new UserSubmissionResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUploadDate(), video.getStatus(), video.getReviewedAt(), video.getRejectionReason());
    }
}
