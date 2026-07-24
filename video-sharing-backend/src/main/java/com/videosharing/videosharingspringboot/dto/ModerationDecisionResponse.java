package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;

import java.time.Instant;

public record ModerationDecisionResponse(
        Long id,
        VideoStatus status,
        Instant reviewedAt,
        String rejectionReason,
        Long version
) {
    public static ModerationDecisionResponse from(Video video) {
        return new ModerationDecisionResponse(video.getId(), video.getStatus(), video.getReviewedAt(),
                video.getRejectionReason(), video.getVersion());
    }
}
