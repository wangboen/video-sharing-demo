package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.WatchHistoryResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/users/me/watch-history")
public class UserWatchHistoryController {

    private final UserService userService;
    private final WatchHistoryService watchHistoryService;

    @Value("${object-storage.public-base-url:}")
    private String objectStoragePublicBaseUrl = "";

    public UserWatchHistoryController(UserService userService, WatchHistoryService watchHistoryService) {
        this.userService = userService;
        this.watchHistoryService = watchHistoryService;
    }

    @GetMapping
    public ResponseEntity<Page<WatchHistoryResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(watchHistoryService.listHistory(
                currentUser(authentication),
                pageRequest(page, size),
                objectStoragePublicBaseUrl));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<WatchHistoryResponse> getOne(
            Authentication authentication,
            @PathVariable Long videoId) {
        Optional<WatchHistoryResponse> record = watchHistoryService.getOne(
                currentUser(authentication),
                videoId,
                objectStoragePublicBaseUrl);
        return record.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<Void> deleteOne(
            Authentication authentication,
            @PathVariable Long videoId) {
        watchHistoryService.deleteOne(currentUser(authentication), videoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication authentication) {
        watchHistoryService.clear(currentUser(authentication));
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
