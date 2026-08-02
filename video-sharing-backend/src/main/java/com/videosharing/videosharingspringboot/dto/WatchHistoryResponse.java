package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.WatchHistory;

import java.math.BigDecimal;
import java.time.Instant;

public record WatchHistoryResponse(
        PublicVideoResponse video,
        int lastPositionSeconds,
        int durationSeconds,
        BigDecimal progressPercent,
        boolean completed,
        Instant firstWatchedAt,
        Instant lastWatchedAt
) {
    public static WatchHistoryResponse from(WatchHistory history, String objectStoragePublicBaseUrl) {
        return new WatchHistoryResponse(
                PublicVideoResponse.from(history.getVideo(), objectStoragePublicBaseUrl),
                history.getLastPositionSeconds(),
                history.getDurationSeconds(),
                history.getProgressPercent(),
                history.isCompleted(),
                history.getFirstWatchedAt(),
                history.getLastWatchedAt());
    }
}
