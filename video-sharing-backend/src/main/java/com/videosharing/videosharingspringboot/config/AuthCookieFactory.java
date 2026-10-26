package com.videosharing.videosharingspringboot.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieFactory {
    private final String userCookieName;
    private final String staffCookieName;
    private final String userCookiePath;
    private final String staffCookiePath;
    private final String sameSite;
    private final boolean secure;
    private final Duration lifetime;

    public AuthCookieFactory(
            @Value("${auth.cookie.user-name:vision_refresh}") String userCookieName,
            @Value("${auth.cookie.staff-name:vision_staff_refresh}") String staffCookieName,
            @Value("${auth.cookie.user-path:/api/auth}") String userCookiePath,
            @Value("${auth.cookie.staff-path:/api/staff/auth}") String staffCookiePath,
            @Value("${auth.cookie.same-site:Lax}") String sameSite,
            @Value("${auth.cookie.secure:true}") boolean secure,
            @Value("${auth.refresh-token-expiration-ms:1209600000}") long refreshLifetimeMs) {
        this.userCookieName = userCookieName;
        this.staffCookieName = staffCookieName;
        this.userCookiePath = userCookiePath;
        this.staffCookiePath = staffCookiePath;
        this.sameSite = sameSite;
        this.secure = secure;
        this.lifetime = Duration.ofMillis(refreshLifetimeMs);
    }

    public String userCookieName() {
        return userCookieName;
    }

    public String staffCookieName() {
        return staffCookieName;
    }

    public String userToken(HttpServletRequest request) {
        return readCookie(request, userCookieName);
    }

    public String staffToken(HttpServletRequest request) {
        return readCookie(request, staffCookieName);
    }

    public ResponseCookie userCookie(String token) {
        return cookie(userCookieName, userCookiePath, token, lifetime);
    }

    public ResponseCookie staffCookie(String token) {
        return cookie(staffCookieName, staffCookiePath, token, lifetime);
    }

    public ResponseCookie clearUserCookie() {
        return cookie(userCookieName, userCookiePath, "", Duration.ZERO);
    }

    public ResponseCookie clearStaffCookie() {
        return cookie(staffCookieName, staffCookiePath, "", Duration.ZERO);
    }

    private ResponseCookie cookie(String name, String path, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
