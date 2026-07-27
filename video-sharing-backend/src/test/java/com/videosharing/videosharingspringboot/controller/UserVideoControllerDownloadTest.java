package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
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
