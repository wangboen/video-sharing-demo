package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.AuthResponse;
import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.dto.RegisterRequest;
import com.videosharing.videosharingspringboot.dto.UserProfileResponse;
import com.videosharing.videosharingspringboot.config.AuthCookieFactory;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.security.CsrfRequestValidator;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.RefreshTokenService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;
    private final RefreshTokenService refreshTokens;
    private final AuthCookieFactory cookies;
    private final CsrfRequestValidator csrfValidator;

    public UserController(UserService userService, JwtService jwtService, RedisSessionService redisSessionService,
                          RefreshTokenService refreshTokens, AuthCookieFactory cookies,
                          CsrfRequestValidator csrfValidator) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.redisSessionService = redisSessionService;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
        this.csrfValidator = csrfValidator;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        User user = userService.registerUser(registerRequest.getUsername(), registerRequest.getEmail(), registerRequest.getPassword());
        return authResponse("User registered successfully!", user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        User user;
        try {
            user = userService.loginUser(loginRequest.getUsername(), loginRequest.getPassword());
        } catch (RuntimeException e) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Invalid username or password");
        }
        return authResponse("User logged in successfully!", user, HttpStatus.OK);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required");
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> ResponseEntity.ok(UserProfileResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        requireValidCsrfRequest(request);
        try {
            RefreshTokenService.Session session = refreshTokens.consume(cookies.userToken(request), JwtService.ACCOUNT_TYPE_USER);
            User user = userService.findUserById(session.userId())
                    .filter(found -> found.getRole() == com.videosharing.videosharingspringboot.model.Role.USER)
                    .orElseThrow(() -> new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required"));
            return authResponse("User session refreshed", user, HttpStatus.OK);
        } catch (ApiExceptionHandler.AuthenticationRequiredException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookies.clearUserCookie().toString())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        requireValidCsrfRequest(request);
        refreshTokens.revoke(cookies.userToken(request), JwtService.ACCOUNT_TYPE_USER);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearUserCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authResponse(String message, User user, HttpStatus status) {
        String token = jwtService.generateToken(user);
        String accessSessionId = jwtService.extractSessionId(token);
        redisSessionService.createSession(accessSessionId, user.getId(), jwtService.getExpiration());
        String refreshToken = refreshTokens.issue(user.getId(), JwtService.ACCOUNT_TYPE_USER, accessSessionId);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.userCookie(refreshToken).toString())
                .body(new AuthResponse(message, token, user.getUsername(), user.getId(), user.getEmail(), user.getRole()));
    }

    private void requireValidCsrfRequest(HttpServletRequest request) {
        if (!csrfValidator.isValid(request)) {
            throw new AccessDeniedException("Invalid CSRF request");
        }
    }
}
