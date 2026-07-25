package com.videosharing.videosharingspringboot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoControllerSafeResponseTest {
    private final VideoService videos = mock(VideoService.class);
    private final UserService users = mock(UserService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private VideoController controller;
    private User creator;

    @BeforeEach
    void setUp() {
        controller = new VideoController();
        ReflectionTestUtils.setField(controller, "videoService", videos);
        ReflectionTestUtils.setField(controller, "userService", users);
        creator = new User();
        creator.setId(7L);
        creator.setUsername("creator");
        creator.setEmail("creator@example.com");
        creator.setRole(Role.USER);
    }

    @Test
    void pendingUploadResponseContainsNoStorageOrModerationInternals() throws Exception {
        Video pending = video(VideoStatus.PENDING);
        pending.setLeaseOwner(staff());
        pending.setLeaseExpiresAt(Instant.parse("2026-07-24T05:00:00Z"));
        var authentication = new UsernamePasswordAuthenticationToken("creator", null);
        var file = new MockMultipartFile("file", "video.mp4", "video/mp4", "video".getBytes());
        when(users.findByUsername("creator")).thenReturn(Optional.of(creator));
        when(videos.uploadVideo(creator, "Title", "Description", file, null)).thenReturn(pending);

        JsonNode body = json.valueToTree(controller.uploadVideo(
                authentication, "Title", "Description", file, null).getBody());

        assertEquals("PENDING", body.get("status").asText());
        assertSafe(body);
        assertFalse(body.has("videoUrl"));
        assertFalse(body.has("thumbnailUrl"));
    }

    @Test
    void publicListExposesApprovedMediaUrlsWithoutEntityInternals() {
        Video approved = video(VideoStatus.APPROVED);
        when(videos.getAllVideos()).thenReturn(List.of(approved));

        JsonNode body = json.valueToTree(controller.getAllVideos().getBody()).get(0);

        assertEquals("/uploads/video.mp4", body.get("videoUrl").asText());
        assertEquals("/thumbnails/thumb.png", body.get("thumbnailUrl").asText());
        assertEquals("creator", body.get("uploader").asText());
        assertSafe(body);
    }

    @Test
    void publicResponseRefusesToCreateMediaUrlsForPendingVideo() {
        when(videos.getAllVideos()).thenReturn(List.of(video(VideoStatus.PENDING)));
        assertThrows(IllegalArgumentException.class, controller::getAllVideos);
    }

    private Video video(VideoStatus status) {
        Video video = new Video();
        video.setId(10L);
        video.setUser(creator);
        video.setTitle("Title");
        video.setDescription("Description");
        video.setFilePath("video.mp4");
        video.setThumbnailPath("thumb.png");
        video.setStatus(status);
        video.setVersion(4L);
        video.setUploadDate(LocalDateTime.of(2026, 7, 24, 12, 0));
        return video;
    }

    private StaffAccount staff() {
        StaffAccount staff = new StaffAccount();
        staff.setUsername("reviewer");
        staff.setRole(Role.REVIEWER);
        return staff;
    }

    private void assertSafe(JsonNode body) {
        assertFalse(body.has("filePath"));
        assertFalse(body.has("thumbnailPath"));
        assertFalse(body.has("reviewer"));
        assertFalse(body.has("reviewedAt"));
        assertFalse(body.has("rejectionReason"));
        assertFalse(body.has("version"));
        assertFalse(body.has("leaseOwner"));
        assertFalse(body.has("claimedAt"));
        assertFalse(body.has("leaseExpiresAt"));
        assertFalse(body.has("user"));
    }
}
