package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.dto.CompleteUploadRequest;
import com.videosharing.videosharingspringboot.dto.CancelUploadRequest;
import com.videosharing.videosharingspringboot.dto.CreateUploadSessionRequest;
import com.videosharing.videosharingspringboot.dto.PublicVideoResponse;
import com.videosharing.videosharingspringboot.dto.VideoUploadResponse;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import com.videosharing.videosharingspringboot.service.VideoSearchUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService; // To fetch user for video upload

    @Value("${object-storage.public-base-url:}")
    private String objectStoragePublicBaseUrl;

    @PostMapping("/upload-session")
    public ResponseEntity<?> createUploadSession(
            Authentication authentication,
            @RequestBody CreateUploadSessionRequest request) {
        try {
            User user = currentUser(authentication);
            return new ResponseEntity<>(videoService.createUploadSession(user, request), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/upload-complete")
    public ResponseEntity<?> completeUpload(
            Authentication authentication,
            @RequestBody CompleteUploadRequest request) {
        try {
            User user = currentUser(authentication);
            Video video = videoService.completeDirectUpload(user, request);
            return new ResponseEntity<>(VideoUploadResponse.from(video), HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>("Failed to complete upload: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/upload-cancel")
    public ResponseEntity<?> cancelUpload(
            Authentication authentication,
            @RequestBody CancelUploadRequest request) {
        try {
            User user = currentUser(authentication);
            videoService.cancelDirectUpload(user, request);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping
    public ResponseEntity<List<PublicVideoResponse>> getAllVideos() {
        List<PublicVideoResponse> videos = videoService.getAllVideos().stream()
                .map(video -> PublicVideoResponse.from(video, objectStoragePublicBaseUrl))
                .toList();
        return new ResponseEntity<>(videos, HttpStatus.OK);
    }

    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicVideoResponse>> getRecommendedVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(videoService.getRecommendedVideos(pageRequest(page, size))
                .map(video -> PublicVideoResponse.from(video, objectStoragePublicBaseUrl)));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchVideos(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        try {
            return ResponseEntity.ok(videoService.searchPublicVideos(q == null ? "" : q.trim(), pageRequest(page, size))
                    .map(video -> PublicVideoResponse.from(video, objectStoragePublicBaseUrl)));
        } catch (VideoSearchUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Video search is temporarily unavailable");
        }
    }

    private User currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
    }

}
