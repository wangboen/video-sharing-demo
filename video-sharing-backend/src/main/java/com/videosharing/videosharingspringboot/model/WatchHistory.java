package com.videosharing.videosharingspringboot.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_video_watch_history")
@Data
public class WatchHistory {
    @EmbeddedId
    private WatchHistoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("videoId")
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "last_position_seconds", nullable = false)
    private int lastPositionSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "first_watched_at", nullable = false)
    private Instant firstWatchedAt;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WatchHistory create(User user, Video video, Instant now) {
        WatchHistory record = new WatchHistory();
        record.setId(new WatchHistoryId(user.getId(), video.getId()));
        record.setUser(user);
        record.setVideo(video);
        record.setFirstWatchedAt(now);
        record.setLastWatchedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}
