package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.WatchProgressRequest;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.WatchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WatchHistoryControllerTest {
    private final UserService users = mock(UserService.class);
    private final WatchHistoryService history = mock(WatchHistoryService.class);
    private final UserWatchHistoryController userController = new UserWatchHistoryController(users, history);
    private final VideoController videoController = new VideoController();
    private final User viewer = new User();

    @BeforeEach
    void setUp() {
        viewer.setId(7L);
        viewer.setUsername("viewer");
        ReflectionTestUtils.setField(videoController, "userService", users);
        ReflectionTestUtils.setField(videoController, "watchHistoryService", history);
        when(users.findByUsername("viewer")).thenReturn(Optional.of(viewer));
    }

    @Test
    void videoProgressEndpointRecordsForCurrentUser() {
        var auth = new UsernamePasswordAuthenticationToken("viewer", null);
        var request = new WatchProgressRequest(40, 100, false, "pause");

        assertEquals(204, videoController.recordWatchProgress(42L, auth, request).getStatusCode().value());

        verify(history).recordProgress(viewer, 42L, request);
    }

    @Test
    void historyEndpointUsesCurrentUserAndSafePagination() {
        var auth = new UsernamePasswordAuthenticationToken("viewer", null);
        when(history.listHistory(viewer, PageRequest.of(0, 50), ""))
                .thenReturn(Page.empty(PageRequest.of(0, 50)));

        var response = userController.list(auth, -1, 99);

        assertEquals(200, response.getStatusCode().value());
        verify(history).listHistory(viewer, PageRequest.of(0, 50), "");
    }

    @Test
    void historyEndpointsRequireAuthentication() {
        assertThrows(ApiExceptionHandler.AuthenticationRequiredException.class,
                () -> userController.clear(null));
    }
}
