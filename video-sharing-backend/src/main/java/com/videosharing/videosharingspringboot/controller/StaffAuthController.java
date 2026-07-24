package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.AuthResponse;
import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/auth")
public class StaffAuthController {
    private final UserService users;
    private final JwtService jwt;
    private final RedisSessionService sessions;

    public StaffAuthController(UserService users, JwtService jwt, RedisSessionService sessions) {
        this.users = users;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = users.loginStaff(request.getUsername(), request.getPassword());
            String token = jwt.generateToken(user);
            sessions.createSession(jwt.extractSessionId(token), user.getId(), jwt.getExpiration());
            return ResponseEntity.ok(new AuthResponse("Staff logged in successfully!", token,
                    user.getUsername(), user.getId(), user.getEmail(), user.getRole()));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }
}
