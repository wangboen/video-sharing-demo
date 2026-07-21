package com.videosharing.videosharingspringboot.security;

import com.videosharing.videosharingspringboot.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha-256";

    @Test
    void generatesAndValidatesATokenForTheUser() {
        User user = new User();
        user.setId(42L);
        user.setUsername("video_creator");
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("video_creator", jwtService.extractUsername(token));
        assertNotNull(jwtService.extractSessionId(token));
    }

    @Test
    void rejectsExpiredTokens() {
        User user = new User();
        user.setId(42L);
        user.setUsername("video_creator");
        JwtService jwtService = new JwtService(SECRET, -1);

        assertFalse(jwtService.isTokenValid(jwtService.generateToken(user)));
    }
}
