package com.videosharing.videosharingspringboot.repository;

import java.time.Instant;

public interface ReviewerStatsProjection {
    Long getReviewerId();

    long getReviewedTotal();

    long getApprovedTotal();

    long getRejectedTotal();

    Instant getLastReviewedAt();
}
