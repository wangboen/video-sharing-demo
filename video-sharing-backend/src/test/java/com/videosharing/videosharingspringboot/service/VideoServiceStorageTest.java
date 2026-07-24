package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoServiceStorageTest {
    @TempDir Path temp;
    private final VideoRepository repository = mock(VideoRepository.class);

    @Test
    void newUploadIsPendingAndStoredOutsidePublicDirectories() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Video saved = service().uploadVideo(user(), "Title", "Description",
                upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "thumb.png", "image/png", png()));

        assertEquals(VideoStatus.PENDING, saved.getStatus());
        assertTrue(Files.exists(temp.resolve("pending-videos").resolve(saved.getFilePath())));
        assertFalse(Files.exists(temp.resolve("public-videos").resolve(saved.getFilePath())));
        assertTrue(saved.getFilePath().matches("[0-9a-f-]{36}\\.mp4"));
        assertTrue(saved.getThumbnailPath().matches("[0-9a-f-]{36}\\.png"));
    }

    @Test
    void serverSelectsExtensionsFromEveryAllowedMediaSignature() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertStoredVideoExtension("movie.mp4", "video/mp4", mp4(), ".mp4");
        assertStoredVideoExtension("movie.webm", "video/webm",
                bytes(0x1A, 0x45, 0xDF, 0xA3), ".webm");
        assertStoredVideoExtension("movie.ogv", "video/ogg",
                "OggS".getBytes(StandardCharsets.US_ASCII), ".ogv");

        assertStoredThumbnailExtension("cover.jpg", "image/jpeg",
                bytes(0xFF, 0xD8, 0xFF), ".jpg");
        assertStoredThumbnailExtension("cover.png", "image/png", png(), ".png");
        assertStoredThumbnailExtension("cover.webp", "image/webp",
                concat("RIFF".getBytes(StandardCharsets.US_ASCII), bytes(0, 0, 0, 0),
                        "WEBP".getBytes(StandardCharsets.US_ASCII)), ".webp");
    }

    @Test
    void neverRetainsAUserControlledFilename() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Video saved = service().uploadVideo(user(), "Title", "Description",
                upload("file", "../../malicious-name.mp4", "video/mp4", mp4()), null);

        assertFalse(saved.getFilePath().contains("malicious-name"));
        assertTrue(saved.getFilePath().matches("[0-9a-f-]{36}\\.mp4"));
    }

    @Test
    void rejectsSpoofedMimeExtensionAndDangerousContentBeforeCopying() {
        assertRejected(upload("file", "attack.mp4", "video/mp4",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)), null);
        assertRejected(upload("file", "attack.svg", "video/mp4", mp4()), null);
        assertRejected(upload("file", "attack.mp4", "text/html", mp4()), null);
        assertRejected(upload("file", "attack.mp4", "video/mp4",
                concat(mp4(), "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))), null);
        assertRejected(upload("file", "attack.mp4", "video/mp4", bytes(0x4D, 0x5A, 0x90, 0)), null);
        assertRejected(upload("file", "attack.mp4", "video/mp4", new byte[0]), null);
        assertRejected(upload("file", "attack.mp4", "video/mp4", bytes(0, 0, 0, 0, 'f', 't')), null);
        assertRejected(upload("file", "cover.png", "image/png", png()), null);
    }

    @Test
    void rejectsSpoofedThumbnailBeforeCopyingTheValidatedVideo() {
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "cover.png", "image/png",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void databaseFailureDeletesPendingFiles() {
        when(repository.save(any())).thenThrow(new RuntimeException("db failed"));
        assertThrows(RuntimeException.class, () -> service().uploadVideo(user(), "Title", "Description",
                upload("file", "video.mp4", "video/mp4", mp4()), null));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
    }

    @Test
    void invalidThumbnailDoesNotLeaveTheVideoInPendingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service().uploadVideo(user(), "Title", "Description",
                upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "notes.txt", "text/plain", "not-image".getBytes(StandardCharsets.UTF_8))));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
    }

    @Test
    void publicListRequestsOnlyApprovedVideos() {
        service().getAllVideos();
        verify(repository).findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.APPROVED);
    }

    @Test
    void publicDetailDoesNotExposePendingId() {
        service().getVideoById(10L);
        verify(repository).findByIdAndStatus(10L, VideoStatus.APPROVED);
    }

    @Test
    void submissionListingIncludesReviewOutcome() {
        Video video = new Video();
        video.setStatus(VideoStatus.REJECTED);
        video.setReviewedAt(Instant.parse("2026-07-24T04:00:00Z"));
        video.setRejectionReason("duplicate");
        when(repository.findAllByUserIdOrderByUploadDateDescIdDesc(7L)).thenReturn(List.of(video));

        var submission = service().getSubmissions(user()).get(0);

        assertEquals(VideoStatus.REJECTED, submission.status());
        assertEquals("duplicate", submission.rejectionReason());
        assertNotNull(submission.reviewedAt());
    }

    private VideoService service() {
        return new VideoService(repository, temp.resolve("pending-videos").toString(),
                temp.resolve("pending-thumbnails").toString());
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setRole(Role.USER);
        return user;
    }

    private void assertStoredVideoExtension(String fileName, String contentType, byte[] content,
                                            String extension) throws Exception {
        Video saved = service().uploadVideo(user(), "Title", "Description",
                upload("file", fileName, contentType, content), null);
        assertTrue(saved.getFilePath().matches("[0-9a-f-]{36}\\" + extension));
    }

    private void assertStoredThumbnailExtension(String fileName, String contentType, byte[] content,
                                                String extension) throws Exception {
        Video saved = service().uploadVideo(user(), "Title", "Description",
                upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", fileName, contentType, content));
        assertTrue(saved.getThumbnailPath().matches("[0-9a-f-]{36}\\" + extension));
    }

    private void assertRejected(MockMultipartFile video, MockMultipartFile thumbnail) {
        assertThrows(IllegalArgumentException.class,
                () -> service().uploadVideo(user(), "Title", "Description", video, thumbnail));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
        assertDirectoryEmpty(temp.resolve("pending-thumbnails"));
        verify(repository, never()).save(any());
    }

    private static MockMultipartFile upload(String field, String fileName, String contentType, byte[] content) {
        return new MockMultipartFile(field, fileName, contentType, content);
    }

    private static byte[] mp4() {
        return bytes(0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm');
    }

    private static byte[] png() {
        return bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private void assertDirectoryEmpty(Path directory) {
        if (Files.notExists(directory)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        } catch (Exception exception) {
            fail(exception);
        }
    }
}
