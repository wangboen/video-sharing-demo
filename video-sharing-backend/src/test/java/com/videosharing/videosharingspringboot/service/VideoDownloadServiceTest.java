package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoDownloadServiceTest {
    @TempDir Path temp;
    private final VideoRepository repository = mock(VideoRepository.class);
    private final FakeObjectStorage storage = new FakeObjectStorage();
    private final Instant now = Instant.parse("2026-07-27T08:00:00Z");

    @Test
    void ownerCanDownloadPendingVideoAndThumbnail() {
        Video pending = video(VideoStatus.PENDING);
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(pending));

        var video = service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO);

        assertEquals("http://minio.local/download/pending/users/7/session/video/movie.mp4", video.url());
        assertEquals("movie.mp4", storage.lastDownloadName());
        var thumbnail = service().createDownload(user(), 10L, VideoService.DownloadFile.THUMBNAIL);
        assertEquals("http://minio.local/download/pending/users/7/session/thumbnail/cover.png", thumbnail.url());
        assertEquals("cover.png", storage.lastDownloadName());
    }

    @Test
    void approvedVideoAndThumbnailRemainDownloadableAfterRetentionWindow() {
        Video approved = video(VideoStatus.APPROVED);
        approved.setReviewedAt(now.minus(Duration.ofDays(30)));
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(approved));

        var target = service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO);

        assertEquals("http://minio.local/download/pending/users/7/session/video/movie.mp4", target.url());
    }

    @Test
    void rejectedVideoAndThumbnailAreDownloadableBeforeRetentionWindowExpires() {
        Video rejected = video(VideoStatus.REJECTED);
        rejected.setReviewedAt(now.minus(Duration.ofDays(6)));
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(rejected));

        var video = service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO);
        var thumbnail = service().createDownload(user(), 10L, VideoService.DownloadFile.THUMBNAIL);

        assertTrue(video.expiresAt().isAfter(now));
        assertEquals("http://minio.local/download/pending/users/7/session/thumbnail/cover.png", thumbnail.url());
    }

    @Test
    void rejectedVideoCannotBeDownloadedAfterRetentionWindowExpires() {
        Video rejected = video(VideoStatus.REJECTED);
        rejected.setReviewedAt(now.minus(Duration.ofDays(7)));
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(rejected));

        assertThrows(VideoService.DownloadExpiredException.class,
                () -> service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO));
        assertEquals(0, storage.presignDownloadCalls());
    }

    @Test
    void deletedVideoOrThumbnailCannotBeDownloaded() {
        Video rejected = video(VideoStatus.REJECTED);
        rejected.setReviewedAt(now.minus(Duration.ofDays(1)));
        rejected.setFileDeletedAt(now.minus(Duration.ofHours(1)));
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(rejected));

        assertThrows(VideoService.DownloadExpiredException.class,
                () -> service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO));
    }

    @Test
    void missingThumbnailReturnsNotFound() {
        Video pending = video(VideoStatus.PENDING);
        pending.setThumbnailPath(null);
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(pending));

        assertThrows(VideoService.DownloadNotFoundException.class,
                () -> service().createDownload(user(), 10L, VideoService.DownloadFile.THUMBNAIL));
    }

    @Test
    void videoFromAnotherUserReturnsNotFound() {
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.empty());

        assertThrows(VideoService.DownloadNotFoundException.class,
                () -> service().createDownload(user(), 10L, VideoService.DownloadFile.VIDEO));
    }

    private VideoService service() {
        return new VideoService(repository, storage, temp.resolve("validation").toString(),
                Duration.ofMinutes(15), Duration.ofDays(7), Clock.fixed(now, ZoneOffset.UTC));
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setRole(Role.USER);
        return user;
    }

    private Video video(VideoStatus status) {
        Video video = new Video();
        video.setId(10L);
        video.setUser(user());
        video.setTitle("Movie Title");
        video.setDescription("Description");
        video.setUploadDate(LocalDateTime.of(2026, 7, 20, 12, 0));
        video.setStatus(status);
        video.setFilePath("pending/users/7/session/video/movie.mp4");
        video.setThumbnailPath("pending/users/7/session/thumbnail/cover.png");
        return video;
    }

    private static final class FakeObjectStorage implements ObjectStorageService {
        private int presignDownloadCalls;
        private String lastDownloadName;

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/upload/" + key), Instant.now().plus(expiration));
        }

        @Override
        public PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration) {
            presignDownloadCalls++;
            lastDownloadName = downloadFileName;
            return new PresignedUpload(URI.create("http://minio.local/download/" + key), Instant.now().plus(expiration));
        }

        @Override
        public StoredObject get(String key) {
            return new StoredObject(key, "video/mp4", 1L, new ByteArrayInputStream(new byte[] {1}));
        }

        @Override
        public void copy(String sourceKey, String targetKey, String contentType) {
        }

        @Override
        public void delete(String key) {
        }

        int presignDownloadCalls() {
            return presignDownloadCalls;
        }

        String lastDownloadName() {
            return lastDownloadName;
        }
    }
}
