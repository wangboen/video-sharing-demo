package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.AuthResponse;
import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    public StaffAuthController(StaffAccountService staffAccounts, JwtService jwt, RedisSessionService sessions) {
        this.staffAccounts = staffAccounts;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            StaffAccount staff = staffAccounts.loginStaff(request.getUsername(), request.getPassword());
            String token = jwt.generateStaffToken(staff);
            sessions.createSession(jwt.extractSessionId(token), staff.getId(), jwt.getExpiration());
            return ResponseEntity.ok(new AuthResponse("Staff logged in successfully!", token,
                    staff.getUsername(), staff.getId(), staff.getEmail(), staff.getRole()));
        } catch (RuntimeException exception) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Invalid username or password");
        }
    }
}
