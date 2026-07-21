package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VideoService {

    @Autowired
    private VideoRepository videoRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.thumbnail-dir}") // Inject thumbnail directory
    private String thumbnailDir;

    public Video uploadVideo(User user, String title, String description, MultipartFile videoFile, MultipartFile thumbnailFile) throws IOException {
        // Handle video file upload
        Path videoUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(videoUploadPath);

        String videoFileName = createStoredFileName(videoFile);
        Path videoTargetLocation = videoUploadPath.resolve(videoFileName);
        Files.copy(videoFile.getInputStream(), videoTargetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Handle thumbnail file upload
        String thumbnailFileName = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            Path thumbnailUploadPath = Paths.get(thumbnailDir).toAbsolutePath().normalize();
            Files.createDirectories(thumbnailUploadPath);

            validateThumbnail(thumbnailFile);
            thumbnailFileName = createStoredFileName(thumbnailFile);
            Path thumbnailTargetLocation = thumbnailUploadPath.resolve(thumbnailFileName);
            Files.copy(thumbnailFile.getInputStream(), thumbnailTargetLocation, StandardCopyOption.REPLACE_EXISTING);
        }

        Video video = new Video();
        video.setUser(user);
        video.setTitle(title);
        video.setDescription(description);
        video.setFilePath(videoFileName);
        video.setThumbnailPath(thumbnailFileName);
        return videoRepository.save(video);
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Optional<Video> getVideoById(Long id) {
        // Increment view count when video is accessed
        Optional<Video> videoOptional = videoRepository.findById(id);
        videoOptional.ifPresent(video -> {
            video.setViews(video.getViews() + 1);
            videoRepository.save(video);
        });
        return videoOptional;
    }

    private String createStoredFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null ? "file" : Paths.get(originalFileName).getFileName().toString();
        return UUID.randomUUID() + "_" + safeFileName;
    }

    private void validateThumbnail(MultipartFile thumbnailFile) {
        String contentType = thumbnailFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Thumbnail must be an image file.");
        }
    }
}
