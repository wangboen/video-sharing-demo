package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private RedisSessionService redisSessionService;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService, jwtService, redisSessionService);
    }

    @Test
    void loginCreatesRedisSessionForTheIssuedJwt() {
        User user = new User();
        user.setId(42L);
        user.setUsername("creator");
        LoginRequest request = new LoginRequest();
        request.setUsername("creator");
        request.setPassword("password123");
        when(userService.loginUser("creator", "password123")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");
        when(jwtService.extractSessionId("signed-jwt")).thenReturn("session-1");
        when(jwtService.getExpiration()).thenReturn(Duration.ofHours(2));

        var response = userController.loginUser(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(redisSessionService).createSession("session-1", 42L, Duration.ofHours(2));
    }

    @Test
    void logoutDeletesOnlyTheCurrentJwtSession() {
        when(jwtService.isTokenValid("signed-jwt")).thenReturn(true);
        when(jwtService.extractSessionId("signed-jwt")).thenReturn("session-1");

        var response = userController.logout("Bearer signed-jwt");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(redisSessionService).deleteSession(eq("session-1"));
    }
}
