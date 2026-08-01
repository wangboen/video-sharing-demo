package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.CompleteUploadRequest;
import com.videosharing.videosharingspringboot.dto.CancelUploadRequest;
import com.videosharing.videosharingspringboot.dto.CreateUploadSessionRequest;
import com.videosharing.videosharingspringboot.dto.UploadFileRequest;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.videosharing.videosharingspringboot.service.ObjectStorageService.PresignedUpload;
import com.videosharing.videosharingspringboot.service.ObjectStorageService.StoredObject;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoServiceObjectStorageTest {
    @TempDir Path temp;
    private final VideoRepository repository = mock(VideoRepository.class);
    private final FakeObjectStorage storage = new FakeObjectStorage();

    @Test
    void createsPendingObjectUploadSessionForVideoAndThumbnail() {
        var response = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));

        assertTrue(response.uploadSessionId().matches("[0-9a-f-]{36}"));
        assertTrue(response.video().key().startsWith("pending/users/7/" + response.uploadSessionId() + "/video/"));
        assertTrue(response.video().key().endsWith(".mp4"));
        assertTrue(response.thumbnail().key().startsWith("pending/users/7/" + response.uploadSessionId() + "/thumbnail/"));
        assertTrue(response.thumbnail().key().endsWith(".png"));
        assertEquals("video/mp4", response.video().contentType());
        assertEquals("image/png", response.thumbnail().contentType());
        assertTrue(response.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void createsUploadSessionUnderConfiguredPendingPrefix() {
        var response = configuredPrefixService().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                null));

        assertTrue(response.video().key().startsWith(
                "incoming/media/users/7/" + response.uploadSessionId() + "/video/"));
    }

    @Test
    void completedDirectUploadValidatesObjectsAndCreatesPendingSubmissionWithoutLocalMediaFiles() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));
        storage.put(session.video().key(), "video/mp4", MediaFixtures.mp4());
        storage.put(session.thumbnail().key(), "image/png", MediaFixtures.png());

        Video saved = service().completeDirectUpload(user(), new CompleteUploadRequest(
                session.uploadSessionId(), session.video().key(), session.thumbnail().key(), "Title", "Description"));

        assertEquals(VideoStatus.PENDING, saved.getStatus());
        assertEquals(session.video().key(), saved.getFilePath());
        assertEquals(session.thumbnail().key(), saved.getThumbnailPath());
        verify(repository).save(saved);
    }

    @Test
    void completedDirectUploadAllowsMp4XmlMetadataWithoutTreatingItAsActiveContent() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] mp4 = MediaFixtures.mp4WithXmlMetadata();
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", mp4.length),
                null));
        storage.put(session.video().key(), "video/mp4", mp4);

        Video saved = service().completeDirectUpload(user(), new CompleteUploadRequest(
                session.uploadSessionId(), session.video().key(), null, "Title", "Description"));

        assertEquals(VideoStatus.PENDING, saved.getStatus());
        verify(repository).save(saved);
    }

    @Test
    void completedDirectUploadRejectsMaliciousObjectWithoutSaving() {
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                null));
        storage.put(session.video().key(), "video/mp4", "<html><script>alert(1)</script></html>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service().completeDirectUpload(user(),
                new CompleteUploadRequest(session.uploadSessionId(), session.video().key(), null, "Title", "Description")));
        assertFalse(storage.contains(session.video().key()));
        verify(repository, never()).save(any());
    }

    @Test
    void completedDirectUploadDeletesUploadedVideoAndThumbnailWhenVideoValidationFails() {
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));
        storage.put(session.video().key(), "video/mp4", "<html><script>alert(1)</script></html>".getBytes());
        storage.put(session.thumbnail().key(), "image/png", MediaFixtures.png());

        assertThrows(IllegalArgumentException.class, () -> service().completeDirectUpload(user(),
                new CompleteUploadRequest(session.uploadSessionId(), session.video().key(), session.thumbnail().key(),
                        "Title", "Description")));

        assertFalse(storage.contains(session.video().key()));
        assertFalse(storage.contains(session.thumbnail().key()));
        verify(repository, never()).save(any());
    }

    @Test
    void completedDirectUploadDeletesUploadedVideoAndThumbnailWhenThumbnailValidationFails() {
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));
        storage.put(session.video().key(), "video/mp4", MediaFixtures.mp4());
        storage.put(session.thumbnail().key(), "image/png", "<svg><script>alert(1)</script></svg>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service().completeDirectUpload(user(),
                new CompleteUploadRequest(session.uploadSessionId(), session.video().key(), session.thumbnail().key(),
                        "Title", "Description")));

        assertFalse(storage.contains(session.video().key()));
        assertFalse(storage.contains(session.thumbnail().key()));
        verify(repository, never()).save(any());
    }

    @Test
    void completedDirectUploadDeletesUploadedVideoAndThumbnailWhenDatabaseSaveFails() {
        when(repository.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));
        storage.put(session.video().key(), "video/mp4", MediaFixtures.mp4());
        storage.put(session.thumbnail().key(), "image/png", MediaFixtures.png());

        assertThrows(IllegalStateException.class, () -> service().completeDirectUpload(user(),
                new CompleteUploadRequest(session.uploadSessionId(), session.video().key(), session.thumbnail().key(),
                        "Title", "Description")));

        assertFalse(storage.contains(session.video().key()));
        assertFalse(storage.contains(session.thumbnail().key()));
    }

    @Test
    void completedDirectUploadDoesNotDeleteObjectsForInvalidUploadKey() {
        storage.put("pending/users/7/session/video/movie.mp4", "video/mp4", MediaFixtures.mp4());

        assertThrows(IllegalArgumentException.class, () -> service().completeDirectUpload(user(),
                new CompleteUploadRequest("session", "pending/users/99/session/video/movie.mp4", null,
                        "Title", "Description")));

        assertTrue(storage.contains("pending/users/7/session/video/movie.mp4"));
        verify(repository, never()).save(any());
    }

    @Test
    void cancelDirectUploadDeletesUploadedVideoAndThumbnailObjects() {
        var session = service().createUploadSession(user(), new CreateUploadSessionRequest(
                "Title",
                "Description",
                new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                new UploadFileRequest("cover.png", "image/png", MediaFixtures.png().length)));
        storage.put(session.video().key(), "video/mp4", MediaFixtures.mp4());
        storage.put(session.thumbnail().key(), "image/png", MediaFixtures.png());

        service().cancelDirectUpload(user(), new CancelUploadRequest(
                session.uploadSessionId(), session.video().key(), session.thumbnail().key()));

        assertFalse(storage.contains(session.video().key()));
        assertFalse(storage.contains(session.thumbnail().key()));
        verify(repository, never()).save(any());
    }

    @Test
    void cancelDirectUploadDoesNotDeleteObjectsForInvalidUploadKey() {
        storage.put("pending/users/7/session/video/movie.mp4", "video/mp4", MediaFixtures.mp4());

        assertThrows(IllegalArgumentException.class, () -> service().cancelDirectUpload(user(),
                new CancelUploadRequest("session", "pending/users/99/session/video/movie.mp4", null)));

        assertTrue(storage.contains("pending/users/7/session/video/movie.mp4"));
    }

    @Test
    void uploadSessionRejectsOversizeThumbnail() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service().createUploadSession(user(), new CreateUploadSessionRequest(
                        "Title",
                        "Description",
                        new UploadFileRequest("movie.mp4", "video/mp4", MediaFixtures.mp4().length),
                        new UploadFileRequest("cover.png", "image/png", 33L * 1024 * 1024))));

        assertTrue(failure.getMessage().contains("32 MB limit"));
    }

    private VideoService service() {
        return new VideoService(repository, storage, temp.resolve("validation").toString(),
                Duration.ofMinutes(15));
    }

    private VideoService configuredPrefixService() {
        return new VideoService(repository, storage, temp.resolve("validation").toString(),
                Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofDays(7), java.time.Clock.systemUTC(),
                "incoming/media/", "published/media/");
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setRole(Role.USER);
        return user;
    }

    private static final class FakeObjectStorage implements ObjectStorageService {
        private final Map<String, Stored> objects = new HashMap<>();

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/" + key), Instant.now().plus(expiration));
        }

        @Override
        public PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/download/" + key), Instant.now().plus(expiration));
        }

        @Override
        public StoredObject get(String key) {
            Stored stored = objects.get(key);
            if (stored == null) {
                throw new IllegalArgumentException("Missing object " + key);
            }
            return new StoredObject(key, stored.contentType(), stored.content().length,
                    new ByteArrayInputStream(stored.content()));
        }

        @Override
        public void copy(String sourceKey, String targetKey, String contentType) {
            Stored stored = objects.get(sourceKey);
            objects.put(targetKey, new Stored(contentType, stored.content()));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        void put(String key, String contentType, byte[] content) {
            objects.put(key, new Stored(contentType, content));
        }

        boolean contains(String key) {
            return objects.containsKey(key);
        }

        private record Stored(String contentType, byte[] content) {
        }
    }
}
