package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.WatchProgressRequest;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WatchHistoryServiceTest {
    private final WatchHistoryRepository history = mock(WatchHistoryRepository.class);
    private final VideoRepository videos = mock(VideoRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC);
    private final WatchHistoryService service = new WatchHistoryService(history, videos, clock);

    @Test
    void recordsFirstProgressForApprovedVideo() {
        User user = user(7L);
        Video video = video(42L, VideoStatus.APPROVED);
        when(videos.findByIdAndStatus(42L, VideoStatus.APPROVED)).thenReturn(Optional.of(video));
        when(history.findByIdUserIdAndIdVideoId(7L, 42L)).thenReturn(Optional.empty());

        service.recordProgress(user, 42L, new WatchProgressRequest(31, 120, false, "heartbeat"));

        verify(history).save(argThat(record ->
                record.getId().getUserId().equals(7L)
                        && record.getId().getVideoId().equals(42L)
                        && record.getLastPositionSeconds() == 31
                        && record.getDurationSeconds() == 120
                        && record.getProgressPercent().compareTo(new java.math.BigDecimal("25.83")) == 0
                        && !record.isCompleted()
                        && record.getLastWatchedAt().equals(Instant.parse("2026-08-02T10:00:00Z"))));
    }

    @Test
    void updatesExistingProgressWithoutRegressingCompletionOrMaxProgress() {
        User user = user(7L);
        Video video = video(42L, VideoStatus.APPROVED);
        var existing = WatchHistory.create(user, video, clock.instant());
        existing.setProgressPercent(new java.math.BigDecimal("98.00"));
        existing.setCompleted(true);
        when(videos.findByIdAndStatus(42L, VideoStatus.APPROVED)).thenReturn(Optional.of(video));
        when(history.findByIdUserIdAndIdVideoId(7L, 42L)).thenReturn(Optional.of(existing));

        service.recordProgress(user, 42L, new WatchProgressRequest(12, 120, false, "pause"));

        assertEquals(12, existing.getLastPositionSeconds());
        assertEquals(new java.math.BigDecimal("98.00"), existing.getProgressPercent());
        assertTrue(existing.isCompleted());
        verify(history).save(existing);
    }

    @Test
    void rejectsMissingOrUnapprovedVideoProgress() {
        when(videos.findByIdAndStatus(99L, VideoStatus.APPROVED)).thenReturn(Optional.empty());

        assertThrows(ApiExceptionHandler.ResourceNotFoundException.class,
                () -> service.recordProgress(user(7L), 99L, new WatchProgressRequest(10, 100, false, "pause")));
    }

    @Test
    void listsHistoryNewestFirstWithPublicVideoData() {
        User user = user(7L);
        Video video = video(42L, VideoStatus.APPROVED);
        var record = WatchHistory.create(user, video, Instant.parse("2026-08-02T10:00:00Z"));
        record.setLastPositionSeconds(44);
        record.setDurationSeconds(100);
        record.setProgressPercent(new java.math.BigDecimal("44.00"));
        when(history.findAllByIdUserIdOrderByLastWatchedAtDesc(7L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));

        var result = service.listHistory(user, PageRequest.of(0, 20), "http://cdn.test");

        assertEquals(1, result.getTotalElements());
        assertEquals(42L, result.getContent().get(0).video().id());
        assertEquals(44, result.getContent().get(0).lastPositionSeconds());
    }

    @Test
    void deletesOnlyCurrentUsersHistory() {
        service.deleteOne(user(7L), 42L);
        service.clear(user(7L));

        verify(history).deleteByIdUserIdAndIdVideoId(7L, 42L);
        verify(history).deleteByIdUserId(7L);
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("viewer-" + id);
        return user;
    }

    private static Video video(long id, VideoStatus status) {
        Video video = new Video();
        video.setId(id);
        video.setStatus(status);
        video.setTitle("Watchable");
        video.setDescription("description");
        video.setFilePath("public/video.mp4");
        video.setThumbnailPath("public/thumb.jpg");
        video.setUploadDate(LocalDateTime.of(2026, 8, 1, 12, 0));
        video.setViews(10L);
        video.setUser(user(3L));
        return video;
    }
}
