package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.dto.PublicVideoResponse;
import com.videosharing.videosharingspringboot.dto.VideoUploadResponse;
import com.videosharing.videosharingspringboot.service.UserService;
import com.videosharing.videosharingspringboot.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService; // To fetch user for video upload

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(
            Authentication authentication,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {
        try {
            User user = userService.findByUsername(authentication.getName())
                                   .orElseThrow(() -> new RuntimeException("User not found"));
            Video video = videoService.uploadVideo(user, title, description, file, thumbnail);
            return new ResponseEntity<>(VideoUploadResponse.from(video), HttpStatus.CREATED);
        } catch (IOException e) {
            return new ResponseEntity<>("Failed to upload video: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping
    public ResponseEntity<List<PublicVideoResponse>> getAllVideos() {
        List<PublicVideoResponse> videos = videoService.getAllVideos().stream()
                .map(PublicVideoResponse::from)
                .toList();
        return new ResponseEntity<>(videos, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicVideoResponse> getVideoById(@PathVariable Long id) {
        return videoService.getVideoById(id)
                .map(video -> new ResponseEntity<>(PublicVideoResponse.from(video), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
