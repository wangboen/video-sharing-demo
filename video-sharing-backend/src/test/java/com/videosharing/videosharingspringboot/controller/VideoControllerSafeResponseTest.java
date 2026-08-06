package com.videosharing.videosharingspringboot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.CompleteUploadRequest;
import com.videosharing.videosharingspringboot.dto.CreateUploadSessionRequest;
import com.videosharing.videosharingspringboot.dto.UploadFileRequest;
import com.videosharing.videosharingspringboot.dto.UploadSessionResponse;
import com.videosharing.videosharingspringboot.dto.UploadTargetResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
        ReflectionTestUtils.setField(controller, "objectStoragePublicBaseUrl", "http://minio.local/video-sharing");
        creator = new User();
        creator.setId(7L);
        creator.setUsername("creator");
        creator.setEmail("creator@example.com");
        creator.setRole(Role.USER);
    }

    @Test
    void publicListExposesApprovedMediaUrlsWithoutEntityInternals() {
        Video approved = video(VideoStatus.APPROVED);
        approved.setFilePath("public/users/7/video/video.mp4");
        approved.setThumbnailPath("public/users/7/thumbnail/thumb.png");
        when(videos.getAllVideos()).thenReturn(List.of(approved));

        JsonNode body = json.valueToTree(controller.getAllVideos().getBody()).get(0);

        assertEquals("http://minio.local/video-sharing/public/users/7/video/video.mp4", body.get("videoUrl").asText());
        assertEquals("http://minio.local/video-sharing/public/users/7/thumbnail/thumb.png", body.get("thumbnailUrl").asText());
        assertEquals("creator", body.get("uploader").asText());
        assertSafe(body);
    }

    @Test
    void publicResponseRefusesToCreateMediaUrlsForPendingVideo() {
        when(videos.getAllVideos()).thenReturn(List.of(video(VideoStatus.PENDING)));
        assertThrows(IllegalArgumentException.class, controller::getAllVideos);
    }

    @Test
    void recommendedVideosReturnPagedPublicVideoResponses() {
        Video recommended = video(VideoStatus.APPROVED);
        recommended.setId(11L);
        recommended.setTitle("Recommended");
        var pageable = PageRequest.of(0, 6);
        when(videos.getRecommendedVideos(null, pageable, null, null))
                .thenReturn(new PageImpl<>(List.of(recommended), pageable, 8));

        var response = controller.getRecommendedVideos(null, 0, 6, null, null).getBody();

        assertEquals(0, response.getNumber());
        assertEquals(6, response.getSize());
        assertEquals(8, response.getTotalElements());
        assertEquals("Recommended", response.getContent().get(0).title());
    }

    @Test
    void recommendedVideosCanBeFilteredByTag() {
        Video recommended = video(VideoStatus.APPROVED);
        recommended.setId(13L);
        recommended.setTitle("Tagged recommendation");
        var pageable = PageRequest.of(0, 6);
        when(videos.getRecommendedVideos(null, pageable, null, 99L))
                .thenReturn(new PageImpl<>(List.of(recommended), pageable, 1));

        var response = controller.getRecommendedVideos(null, 0, 6, 99L, null).getBody();

        assertEquals(1, response.getTotalElements());
        assertEquals("Tagged recommendation", response.getContent().get(0).title());
    }

    @Test
    void recommendedVideosCanBeFilteredByCategory() {
        Video recommended = video(VideoStatus.APPROVED);
        recommended.setId(14L);
        recommended.setTitle("Category recommendation");
        var pageable = PageRequest.of(0, 6);
        when(videos.getRecommendedVideos(null, pageable, 5L, null))
                .thenReturn(new PageImpl<>(List.of(recommended), pageable, 1));

        var response = controller.getRecommendedVideos(null, 0, 6, null, 5L).getBody();

        assertEquals(1, response.getTotalElements());
        assertEquals("Category recommendation", response.getContent().get(0).title());
    }

    @Test
    void searchVideosReturnPagedPublicVideoResponsesForQuery() {
        Video matched = video(VideoStatus.APPROVED);
        matched.setId(12L);
        matched.setTitle("Spring tutorial");
        var pageable = PageRequest.of(0, 5);
        when(videos.searchPublicVideos("spring", pageable)).thenReturn(new PageImpl<>(List.of(matched), pageable, 1));

        @SuppressWarnings("unchecked")
        Page<com.videosharing.videosharingspringboot.dto.PublicVideoResponse> response =
                (Page<com.videosharing.videosharingspringboot.dto.PublicVideoResponse>)
                        controller.searchVideos(" spring ", 0, 5).getBody();

        assertEquals(0, response.getNumber());
        assertEquals(1, response.getTotalElements());
        assertEquals("Spring tutorial", response.getContent().get(0).title());
    }

    @Test
    void searchVideosReturnsServiceUnavailableWhenDatabaseSearchIsDown() {
        var pageable = PageRequest.of(0, 5);
        when(videos.searchPublicVideos("spring", pageable))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        var exception = assertThrows(DataAccessResourceFailureException.class,
                () -> controller.searchVideos(" spring ", 0, 5));
        var response = new ApiExceptionHandler().handleServiceUnavailable(exception);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Service is temporarily unavailable", response.getBody().get("message"));
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

    @Test
    void uploadSessionAndCompletionResponsesContainNoStorageInternalsBeyondObjectKeys() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken("creator", null);
        var sessionRequest = new CreateUploadSessionRequest("Title", "Description",
                new UploadFileRequest("video.mp4", "video/mp4", 120L),
                new UploadFileRequest("thumb.png", "image/png", 80L));
        var session = new UploadSessionResponse("session-id",
                new UploadTargetResponse("pending/users/7/session-id/video/video.mp4",
                        "http://minio.local/upload-video", "video/mp4"),
                new UploadTargetResponse("pending/users/7/session-id/thumbnail/thumb.png",
                        "http://minio.local/upload-thumb", "image/png"),
                Instant.parse("2026-07-24T05:00:00Z"));
        Video pending = video(VideoStatus.PENDING);
        pending.setFilePath(session.video().key());
        pending.setThumbnailPath(session.thumbnail().key());
        when(users.findByUsername("creator")).thenReturn(Optional.of(creator));
        when(videos.createUploadSession(creator, sessionRequest)).thenReturn(session);
        when(videos.completeDirectUpload(creator, new CompleteUploadRequest(
                "session-id", session.video().key(), session.thumbnail().key(), "Title", "Description")))
                .thenReturn(pending);

        JsonNode sessionBody = json.valueToTree(controller.createUploadSession(authentication, sessionRequest).getBody());
        JsonNode completeBody = json.valueToTree(controller.completeUpload(authentication, new CompleteUploadRequest(
                "session-id", session.video().key(), session.thumbnail().key(), "Title", "Description")).getBody());

        assertEquals("http://minio.local/upload-video", sessionBody.get("video").get("uploadUrl").asText());
        assertEquals("PENDING", completeBody.get("status").asText());
        assertSafe(completeBody);
        assertFalse(completeBody.has("videoUrl"));
        assertFalse(completeBody.has("thumbnailUrl"));
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
