package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

import java.time.Instant;

public record ReviewerReviewRecordResponse(
        Long videoId,
        String title,
        String uploader,
        VideoStatus status,
        Instant reviewedAt,
        String rejectionReason
) {
    public static ReviewerReviewRecordResponse from(Video video) {
        return new ReviewerReviewRecordResponse(video.getId(), video.getTitle(),
                video.getUser().getUsername(), video.getStatus(), video.getReviewedAt(),
                video.getRejectionReason());
    }
}
