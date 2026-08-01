package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.dto.UserSubmissionResponse;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserVideoControllerDownloadTest {
    private final UserService users = mock(UserService.class);
    private final VideoService videos = mock(VideoService.class);
    private final UserVideoController controller = new UserVideoController(users, videos);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("creator", null);
    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(7L);
        creator.setUsername("creator");
        creator.setRole(Role.USER);
        when(users.findByUsername("creator")).thenReturn(Optional.of(creator));
    }

    @Test
    void listReturnsRequestedSubmissionPageForCurrentUser() {
        var pageable = PageRequest.of(2, 5);
        var submission = new UserSubmissionResponse(42L, "Paged video", "Description",
                LocalDateTime.of(2026, 7, 31, 10, 0), VideoStatus.PENDING, null, null,
                true, false, null, null, null);
        var anotherSubmission = new UserSubmissionResponse(43L, "Another paged video", "Description",
                LocalDateTime.of(2026, 7, 31, 9, 0), VideoStatus.APPROVED, null, null,
                true, true, null, null, null);
        when(videos.getSubmissions(creator, pageable))
                .thenReturn(new PageImpl<>(List.of(submission, anotherSubmission), pageable, 12));

        var page = controller.list(2, 5, authentication);

        assertEquals(2, page.getNumber());
        assertEquals(5, page.getSize());
        assertEquals(12, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Paged video", page.getContent().get(0).title());
    }

    @Test
    void downloadableVideoRedirectsToPresignedObjectStorageUrl() {
        when(videos.createDownload(creator, 42L, VideoService.DownloadFile.VIDEO))
                .thenReturn(new VideoService.VideoDownloadTarget(
                        "http://minio.local/download/video.mp4", Instant.parse("2026-07-27T08:15:00Z")));

        var response = controller.downloadVideo(42L, true, authentication);

        assertEquals(302, response.getStatusCode().value());
        assertEquals("http://minio.local/download/video.mp4", response.getHeaders().getLocation().toString());
    }

    @Test
    void downloadableVideoCanReturnJsonTargetForAuthenticatedFrontendClick() {
        when(videos.createDownload(creator, 42L, VideoService.DownloadFile.VIDEO))
                .thenReturn(new VideoService.VideoDownloadTarget(
                        "http://minio.local/download/video.mp4", Instant.parse("2026-07-27T08:15:00Z")));

        var response = controller.downloadVideo(42L, false, authentication);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("http://minio.local/download/video.mp4", body.get("url"));
        assertEquals(Instant.parse("2026-07-27T08:15:00Z"), body.get("expiresAt"));
    }

    @Test
    void expiredThumbnailDownloadReturnsGone() {
        when(videos.createDownload(creator, 42L, VideoService.DownloadFile.THUMBNAIL))
                .thenThrow(new VideoService.DownloadExpiredException("expired"));

        var response = controller.downloadThumbnail(42L, true, authentication);

        assertEquals(410, response.getStatusCode().value());
    }
}
