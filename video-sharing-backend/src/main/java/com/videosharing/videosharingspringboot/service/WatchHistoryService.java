package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.WatchHistoryResponse;
import com.videosharing.videosharingspringboot.dto.WatchProgressRequest;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class WatchHistoryService {
    private final WatchHistoryRepository history;
    private final VideoRepository videos;
    private final Clock clock;

    @Autowired
    public WatchHistoryService(WatchHistoryRepository history, VideoRepository videos) {
        this(history, videos, Clock.systemUTC());
    }

    WatchHistoryService(WatchHistoryRepository history, VideoRepository videos, Clock clock) {
        this.history = history;
        this.videos = videos;
        this.clock = clock;
    }

    @Transactional
    public void recordProgress(User user, Long videoId, WatchProgressRequest request) {
        Video video = videos.findByIdAndStatus(videoId, VideoStatus.APPROVED)
                .orElseThrow(() -> new ApiExceptionHandler.ResourceNotFoundException("Video not found"));
        Instant now = clock.instant();
        WatchHistory record = history.findByIdUserIdAndIdVideoId(user.getId(), videoId)
                .orElseGet(() -> WatchHistory.create(user, video, now));

        int position = Math.max(0, request == null || request.positionSeconds() == null ? 0 : request.positionSeconds());
        int duration = Math.max(0, request == null || request.durationSeconds() == null ? 0 : request.durationSeconds());
        BigDecimal progress = progressPercent(position, duration);
        boolean completed = Boolean.TRUE.equals(request == null ? null : request.completed())
                || progress.compareTo(new BigDecimal("95.00")) >= 0;

        record.setLastPositionSeconds(position);
        record.setDurationSeconds(Math.max(record.getDurationSeconds(), duration));
        if (record.getProgressPercent() == null || record.getProgressPercent().compareTo(progress) < 0) {
            record.setProgressPercent(progress);
        }
        record.setCompleted(record.isCompleted() || completed);
        record.setLastWatchedAt(now);
        record.setUpdatedAt(now);
        history.save(record);
    }

    @Transactional(readOnly = true)
    public Page<WatchHistoryResponse> listHistory(User user, Pageable pageable, String objectStoragePublicBaseUrl) {
        return history.findAllByIdUserIdOrderByLastWatchedAtDesc(user.getId(), pageable)
                .map(record -> WatchHistoryResponse.from(record, objectStoragePublicBaseUrl));
    }

    @Transactional(readOnly = true)
    public Optional<WatchHistoryResponse> getOne(User user, Long videoId, String objectStoragePublicBaseUrl) {
        return history.findByIdUserIdAndIdVideoId(user.getId(), videoId)
                .map(record -> WatchHistoryResponse.from(record, objectStoragePublicBaseUrl));
    }

    @Transactional
    public void deleteOne(User user, Long videoId) {
        history.deleteByIdUserIdAndIdVideoId(user.getId(), videoId);
    }

    @Transactional
    public void clear(User user) {
        history.deleteByIdUserId(user.getId());
    }

    private static BigDecimal progressPercent(int position, int duration) {
        if (duration <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        int cappedPosition = Math.min(position, duration);
        return BigDecimal.valueOf(cappedPosition)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(duration), 2, RoundingMode.HALF_UP);
    }
}
