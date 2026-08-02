package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.UserSubmissionResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

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
    public Page<UserSubmissionResponse> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             Authentication authentication) {
        User user = currentUser(authentication);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        return videos.getSubmissions(user, PageRequest.of(safePage, safeSize));
    }

    @GetMapping("/{id}/download/video")
    public ResponseEntity<?> downloadVideo(@PathVariable Long id,
                                           @RequestParam(defaultValue = "true") boolean redirect,
                                           Authentication authentication) {
        return download(id, redirect, authentication, VideoService.DownloadFile.VIDEO);
    }

    @GetMapping("/{id}/download/thumbnail")
    public ResponseEntity<?> downloadThumbnail(@PathVariable Long id,
                                               @RequestParam(defaultValue = "true") boolean redirect,
                                               Authentication authentication) {
        return download(id, redirect, authentication, VideoService.DownloadFile.THUMBNAIL);
    }

    private ResponseEntity<?> download(Long id, boolean redirect, Authentication authentication, VideoService.DownloadFile file) {
        User user = currentUser(authentication);
        try {
            VideoService.VideoDownloadTarget target = videos.createDownload(user, id, file);
            if (redirect) {
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target.url())).build();
            }
            return ResponseEntity.ok(Map.of("url", target.url(), "expiresAt", target.expiresAt()));
        } catch (VideoService.DownloadNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (VideoService.DownloadExpiredException exception) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required");
        }
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiExceptionHandler.AuthenticationRequiredException("User not found"));
    }
}
