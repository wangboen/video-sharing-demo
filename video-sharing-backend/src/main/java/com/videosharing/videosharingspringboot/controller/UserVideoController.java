package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.UserSubmissionResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/videos")
public class UserVideoController {
    private final UserService users;
    private final VideoService videos;

    public UserVideoController(UserService users, VideoService videos) {
        this.users = users;
        this.videos = videos;
    }

    @GetMapping
    public List<UserSubmissionResponse> list(Authentication authentication) {
        User user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new CurrentUserNotFoundException());
        return videos.getSubmissions(user);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class CurrentUserNotFoundException extends RuntimeException {
    }
}
