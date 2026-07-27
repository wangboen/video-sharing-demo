package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RejectedVideoCleanupServiceTest {
    private final VideoRepository repository = mock(VideoRepository.class);
    private final FakeObjectStorage storage = new FakeObjectStorage();
    private final Instant now = Instant.parse("2026-07-27T08:00:00Z");

    @Test
    void deletesExpiredRejectedVideoAndThumbnailObjectsAndMarksDeletionTimes() {
        Video expired = rejected(now.minus(Duration.ofDays(8)));
        when(repository.findRejectedWithDownloadableObjectsBefore(
                VideoStatus.REJECTED, now.minus(Duration.ofDays(7)), PageRequest.of(0, 100)))
                .thenReturn(List.of(expired));

        service().cleanupExpiredRejectedFiles();

        assertEquals(List.of("pending/users/7/session/video/movie.mp4",
                "pending/users/7/session/thumbnail/cover.png"), storage.deletedKeys());
        assertEquals(now, expired.getFileDeletedAt());
        assertEquals(now, expired.getThumbnailDeletedAt());
        verify(repository).save(expired);
    }

    @Test
    void doesNotMarkVideoDeletedWhenObjectStorageDeleteFails() {
        Video expired = rejected(now.minus(Duration.ofDays(8)));
        storage.failKey("pending/users/7/session/video/movie.mp4");
        when(repository.findRejectedWithDownloadableObjectsBefore(
                VideoStatus.REJECTED, now.minus(Duration.ofDays(7)), PageRequest.of(0, 100)))
                .thenReturn(List.of(expired));

        service().cleanupExpiredRejectedFiles();

        assertNull(expired.getFileDeletedAt());
        assertEquals(now, expired.getThumbnailDeletedAt());
        verify(repository).save(expired);
    }

    @Test
    void missingObjectIsTreatedAsDeletedSoCleanupIsIdempotent() {
        Video expired = rejected(now.minus(Duration.ofDays(8)));
        storage.missingKey("pending/users/7/session/video/movie.mp4");
        when(repository.findRejectedWithDownloadableObjectsBefore(
                VideoStatus.REJECTED, now.minus(Duration.ofDays(7)), PageRequest.of(0, 100)))
                .thenReturn(List.of(expired));

        service().cleanupExpiredRejectedFiles();

        assertEquals(now, expired.getFileDeletedAt());
        assertEquals(now, expired.getThumbnailDeletedAt());
    }

    private RejectedVideoCleanupService service() {
        return new RejectedVideoCleanupService(repository, storage, Duration.ofDays(7),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private Video rejected(Instant reviewedAt) {
        Video video = new Video();
        video.setId(10L);
        video.setTitle("Movie Title");
        video.setUploadDate(LocalDateTime.of(2026, 7, 20, 12, 0));
        video.setStatus(VideoStatus.REJECTED);
        video.setReviewedAt(reviewedAt);
        video.setFilePath("pending/users/7/session/video/movie.mp4");
        video.setThumbnailPath("pending/users/7/session/thumbnail/cover.png");
        return video;
    }

    private static final class FakeObjectStorage implements ObjectStorageService {
        private final List<String> deletedKeys = new ArrayList<>();
        private final List<String> failedKeys = new ArrayList<>();
        private final List<String> missingKeys = new ArrayList<>();

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/upload/" + key), Instant.now().plus(expiration));
        }

        @Override
        public PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration) {
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
            if (failedKeys.contains(key)) {
                throw new IllegalStateException("minio unavailable");
            }
            if (missingKeys.contains(key)) {
                throw new ObjectStorageService.ObjectNotFoundException(key);
            }
            deletedKeys.add(key);
        }

        void failKey(String key) {
            failedKeys.add(key);
        }

        void missingKey(String key) {
            missingKeys.add(key);
        }

        List<String> deletedKeys() {
            return deletedKeys;
        }
    }
}
