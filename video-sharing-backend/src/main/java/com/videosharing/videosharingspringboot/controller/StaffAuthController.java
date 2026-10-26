package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.config.AuthCookieFactory;
import com.videosharing.videosharingspringboot.dto.AuthResponse;
import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.CsrfRequestValidator;
import com.videosharing.videosharingspringboot.security.RefreshTokenService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/auth")
public class StaffAuthController {
    private final StaffAccountService staffAccounts;
    private final JwtService jwt;
    private final RedisSessionService sessions;
    private final RefreshTokenService refreshTokens;
    private final AuthCookieFactory cookies;
    private final CsrfRequestValidator csrfValidator;

    public StaffAuthController(StaffAccountService staffAccounts, JwtService jwt, RedisSessionService sessions,
                               RefreshTokenService refreshTokens, AuthCookieFactory cookies,
                               CsrfRequestValidator csrfValidator) {
        this.staffAccounts = staffAccounts;
        this.jwt = jwt;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
        this.csrfValidator = csrfValidator;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        StaffAccount staff;
        try {
            staff = staffAccounts.loginStaff(request.getUsername(), request.getPassword());
        } catch (RuntimeException exception) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Invalid username or password");
        }
        return authResponse("Staff logged in successfully!", staff, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        requireValidCsrfRequest(request);
        try {
            RefreshTokenService.Session session = refreshTokens.consume(cookies.staffToken(request), JwtService.ACCOUNT_TYPE_STAFF);
            StaffAccount staff = staffAccounts.findStaffById(session.userId())
                    .orElseThrow(() -> new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required"));
            return authResponse("Staff session refreshed", staff, HttpStatus.OK);
        } catch (ApiExceptionHandler.AuthenticationRequiredException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookies.clearStaffCookie().toString())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        requireValidCsrfRequest(request);
        refreshTokens.revoke(cookies.staffToken(request), JwtService.ACCOUNT_TYPE_STAFF);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearStaffCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authResponse(String message, StaffAccount staff, HttpStatus status) {
        String token = jwt.generateStaffToken(staff);
        String accessSessionId = jwt.extractSessionId(token);
        sessions.createSession(accessSessionId, staff.getId(), jwt.getExpiration());
        String refreshToken = refreshTokens.issue(staff.getId(), JwtService.ACCOUNT_TYPE_STAFF, accessSessionId);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.staffCookie(refreshToken).toString())
                .body(new AuthResponse(message, token, staff.getUsername(), staff.getId(), staff.getEmail(), staff.getRole()));
    }

    private void requireValidCsrfRequest(HttpServletRequest request) {
        if (!csrfValidator.isValid(request)) {
            throw new AccessDeniedException("Invalid CSRF request");
        }
    }
}
