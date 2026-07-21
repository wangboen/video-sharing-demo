package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.AuthResponse;
import com.videosharing.videosharingspringboot.dto.LoginRequest;
import com.videosharing.videosharingspringboot.dto.RegisterRequest;
import com.videosharing.videosharingspringboot.dto.UserProfileResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.security.JwtService;
import com.videosharing.videosharingspringboot.security.RedisSessionService;
import com.videosharing.videosharingspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;

    public UserController(UserService userService, JwtService jwtService, RedisSessionService redisSessionService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.redisSessionService = redisSessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            User user = userService.registerUser(registerRequest.getUsername(), registerRequest.getEmail(), registerRequest.getPassword());
            return new ResponseEntity<>(authResponse("User registered successfully!", user), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            User user = userService.loginUser(loginRequest.getUsername(), loginRequest.getPassword());
            return new ResponseEntity<>(authResponse("User logged in successfully!", user), HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .map(user -> ResponseEntity.ok(UserProfileResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization.substring("Bearer ".length());
        if (jwtService.isTokenValid(token)) {
            redisSessionService.deleteSession(jwtService.extractSessionId(token));
        }
        return ResponseEntity.noContent().build();
    }

    private AuthResponse authResponse(String message, User user) {
        String token = jwtService.generateToken(user);
        redisSessionService.createSession(jwtService.extractSessionId(token), user.getId(), jwtService.getExpiration());
        return new AuthResponse(message, token, user.getUsername(), user.getId(), user.getEmail());
    }
}
