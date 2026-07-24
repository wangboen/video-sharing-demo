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
import java.time.LocalDateTime;
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
                new MockMultipartFile("file", "video.mp4", "video/mp4", "video".getBytes()),
                new MockMultipartFile("thumbnail", "thumb.png", "image/png", "thumb".getBytes()));

        assertEquals(VideoStatus.PENDING, saved.getStatus());
        assertTrue(Files.exists(temp.resolve("pending-videos").resolve(saved.getFilePath())));
        assertFalse(Files.exists(temp.resolve("public-videos").resolve(saved.getFilePath())));
    }

    @Test
    void databaseFailureDeletesPendingFiles() {
        when(repository.save(any())).thenThrow(new RuntimeException("db failed"));
        assertThrows(RuntimeException.class, () -> service().uploadVideo(user(), "Title", "Description",
                new MockMultipartFile("file", "video.mp4", "video/mp4", "video".getBytes()), null));
        assertDirectoryEmpty(temp.resolve("pending-videos"));
    }

    @Test
    void invalidThumbnailDoesNotLeaveTheVideoInPendingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service().uploadVideo(user(), "Title", "Description",
                new MockMultipartFile("file", "video.mp4", "video/mp4", "video".getBytes()),
                new MockMultipartFile("thumbnail", "notes.txt", "text/plain", "not-image".getBytes())));
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
        video.setReviewedAt(LocalDateTime.of(2026, 7, 24, 12, 0));
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
