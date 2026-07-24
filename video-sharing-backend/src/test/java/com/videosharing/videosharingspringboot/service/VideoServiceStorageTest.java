package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
                MediaFixtures.webm(), ".webm");
        assertStoredVideoExtension("movie.ogv", "video/ogg",
                MediaFixtures.ogg(), ".ogv");

        assertStoredThumbnailExtension("cover.jpg", "image/jpeg",
                MediaFixtures.jpeg(), ".jpg");
        assertStoredThumbnailExtension("cover.png", "image/png", png(), ".png");
        assertStoredThumbnailExtension("cover.webp", "image/webp",
                MediaFixtures.webp(), ".webp");
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
    void rejectsPrefixOnlyTruncatedContainersAndImagesWithoutLeavingTemporaryFiles() {
        assertRejected(upload("file", "truncated.mp4", "video/mp4",
                bytes(0, 0, 0, 8, 'f', 't', 'y', 'p')), null);
        assertRejected(upload("file", "truncated.webm", "video/webm",
                bytes(0x1A, 0x45, 0xDF, 0xA3)), null);
        assertRejected(upload("file", "truncated.ogv", "video/ogg",
                "OggS".getBytes(StandardCharsets.US_ASCII)), null);
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "truncated.jpg", "image/jpeg", bytes(0xFF, 0xD8, 0xFF)));
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "truncated.png", "image/png",
                        bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "truncated.webp", "image/webp",
                        concat("RIFF".getBytes(StandardCharsets.US_ASCII), bytes(0, 0, 0, 0),
                                "WEBP".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void rejectsHeaderOnlyAndTruncatedWebpContainersInsteadOfTrustingChunkPrefixes() {
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "header-only.webp", "image/webp",
                        MediaFixtures.webpHeaderOnly()));
        byte[] truncated = java.util.Arrays.copyOf(
                MediaFixtures.webp(), MediaFixtures.webp().length - 1);
        assertRejected(upload("file", "video.mp4", "video/mp4", mp4()),
                upload("thumbnail", "truncated.webp", "image/webp", truncated));
    }

    @Test
    void enforcesWebpPixelLimitForVp8Vp8lAndVp8xHeaders() {
        for (byte[] oversized : List.of(
                MediaFixtures.oversizedVp8Webp(),
                MediaFixtures.oversizedVp8lWebp(),
                MediaFixtures.oversizedVp8xWebp())) {
            IllegalArgumentException failure = rejected(
                    upload("file", "video.mp4", "video/mp4", mp4()),
                    upload("thumbnail", "oversized.webp", "image/webp", oversized));
            assertTrue(failure.getMessage().contains("dimensions are invalid or too large"));
        }
    }

    @Test
    void rejectsCompleteOggPageFollowedByATruncatedSecondPage() {
        assertRejected(upload("file", "truncated-second-page.ogv", "video/ogg",
                concat(MediaFixtures.ogg(), bytes('O', 'g', 'g', 'S', 0))), null);
    }

    @Test
    void acceptsMultipleCompleteOggPagesEndingExactlyAtEof() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Video saved = service().uploadVideo(user(), "Title", "Description",
                upload("file", "multi-page.ogv", "video/ogg",
                        concat(MediaFixtures.ogg(), MediaFixtures.ogg())), null);

        assertTrue(saved.getFilePath().endsWith(".ogv"));
    }

    @Test
    void rejectsTruncatedOggSegmentTableAndBodySizes() {
        byte[] truncatedTable = new byte[28];
        System.arraycopy(MediaFixtures.ogg(), 0, truncatedTable, 0, 27);
        truncatedTable[26] = 2;
        truncatedTable[27] = 1;
        byte[] truncatedBody = new byte[28];
        System.arraycopy(MediaFixtures.ogg(), 0, truncatedBody, 0, 27);
        truncatedBody[26] = 1;
        truncatedBody[27] = 5;

        assertRejected(upload("file", "truncated-table.ogv", "video/ogg", truncatedTable), null);
        assertRejected(upload("file", "truncated-body.ogv", "video/ogg", truncatedBody), null);
    }

    @Test
    void streamsLargeUploadsWithoutCallingGetBytesOrLeavingTemporaryFiles() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        long payloadSize = 8L * 1024 * 1024;

        Video saved = service().uploadVideo(user(), "Title", "Description",
                new StreamingMultipartFile("large.mp4", "video/mp4", payloadSize), null);

        Path stored = temp.resolve("pending-videos").resolve(saved.getFilePath());
        assertEquals(payloadSize + 40, Files.size(stored));
        try (var files = Files.list(stored.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".upload-")));
        }
    }

    @Test
    void rejectsUploadsBeyondTheStreamingLimitAndCleansTheTemporaryFile() {
        long payloadSize = 33L * 1024 * 1024;

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service().uploadVideo(user(), "Title", "Description",
                        new StreamingMultipartFile("too-large.mp4", "video/mp4", payloadSize), null));

        assertTrue(failure.getMessage().contains("32 MB limit"));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
        verify(repository, never()).save(any());
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
        rejected(video, thumbnail);
    }

    private IllegalArgumentException rejected(MockMultipartFile video, MockMultipartFile thumbnail) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service().uploadVideo(user(), "Title", "Description", video, thumbnail));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
        assertDirectoryEmpty(temp.resolve("pending-thumbnails"));
        verify(repository, never()).save(any());
        return failure;
    }

    private static MockMultipartFile upload(String field, String fileName, String contentType, byte[] content) {
        return new MockMultipartFile(field, fileName, contentType, content);
    }

    private static byte[] mp4() {
        return MediaFixtures.mp4();
    }

    private static byte[] png() {
        return MediaFixtures.png();
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

    private static final class StreamingMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final long payloadSize;

        private StreamingMultipartFile(String originalFilename, String contentType, long payloadSize) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.payloadSize = payloadSize;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return payloadSize + 40;
        }

        @Override
        public byte[] getBytes() {
            throw new AssertionError("getBytes must not be called");
        }

        @Override
        public InputStream getInputStream() {
            return MediaFixtures.largeMp4(payloadSize);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            throw new AssertionError("transferTo must not be called");
        }
    }
}
