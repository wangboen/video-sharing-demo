package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.dto.CompleteUploadRequest;
import com.videosharing.videosharingspringboot.dto.CancelUploadRequest;
import com.videosharing.videosharingspringboot.dto.CreateUploadSessionRequest;
import com.videosharing.videosharingspringboot.dto.PublicVideoResponse;
import com.videosharing.videosharingspringboot.dto.VideoUploadResponse;
import com.videosharing.videosharingspringboot.dto.WatchProgressRequest;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import com.videosharing.videosharingspringboot.service.WatchHistoryService;
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

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Value("${object-storage.public-base-url:}")
    private String objectStoragePublicBaseUrl;

    @PostMapping("/upload-session")
    public ResponseEntity<?> createUploadSession(
            Authentication authentication,
            @RequestBody CreateUploadSessionRequest request) {
        User user = currentUser(authentication);
        return new ResponseEntity<>(videoService.createUploadSession(user, request), HttpStatus.CREATED);
    }

    @PostMapping("/upload-complete")
    public ResponseEntity<?> completeUpload(
            Authentication authentication,
            @RequestBody CompleteUploadRequest request) throws IOException {
        User user = currentUser(authentication);
        Video video = videoService.completeDirectUpload(user, request);
        return new ResponseEntity<>(VideoUploadResponse.from(video), HttpStatus.CREATED);
    }

    @PostMapping("/upload-cancel")
    public ResponseEntity<?> cancelUpload(
            Authentication authentication,
            @RequestBody CancelUploadRequest request) {
        User user = currentUser(authentication);
        videoService.cancelDirectUpload(user, request);
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.ok(videoService.searchPublicVideos(q == null ? "" : q.trim(), pageRequest(page, size))
                .map(video -> PublicVideoResponse.from(video, objectStoragePublicBaseUrl)));
    }

    @PostMapping("/{id}/watch-progress")
    public ResponseEntity<Void> recordWatchProgress(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody WatchProgressRequest request) {
        watchHistoryService.recordProgress(currentUser(authentication), id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/watch-progress/beacon")
    public ResponseEntity<Void> recordWatchProgressBeacon(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody WatchProgressRequest request) {
        watchHistoryService.recordProgress(currentUser(authentication), id, request);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required");
        }
        return userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiExceptionHandler.AuthenticationRequiredException("User not found"));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
    }

}
