package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.dto.UserSubmissionResponse;
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

    private final VideoRepository videoRepository;
    private final Path pendingUploadPath;
    private final Path pendingThumbnailPath;

    public VideoService(VideoRepository videoRepository,
                        @Value("${file.pending-upload-dir}") String pendingUploadDir,
                        @Value("${file.pending-thumbnail-dir}") String pendingThumbnailDir) {
        this.videoRepository = videoRepository;
        this.pendingUploadPath = Paths.get(pendingUploadDir).toAbsolutePath().normalize();
        this.pendingThumbnailPath = Paths.get(pendingThumbnailDir).toAbsolutePath().normalize();
    }

    public Video uploadVideo(User user, String title, String description, MultipartFile videoFile, MultipartFile thumbnailFile) throws IOException {
        if (user.getRole() != Role.USER) {
            throw new IllegalArgumentException("Only users may upload videos");
        }
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            validateThumbnail(thumbnailFile);
        }
        // Handle video file upload
        Files.createDirectories(pendingUploadPath);

        String videoFileName = createStoredFileName(videoFile);
        Path videoTargetLocation = pendingUploadPath.resolve(videoFileName);
        Files.copy(videoFile.getInputStream(), videoTargetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Handle thumbnail file upload
        String thumbnailFileName = null;
        Path thumbnailTargetLocation = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            Files.createDirectories(pendingThumbnailPath);

            thumbnailFileName = createStoredFileName(thumbnailFile);
            thumbnailTargetLocation = pendingThumbnailPath.resolve(thumbnailFileName);
            Files.copy(thumbnailFile.getInputStream(), thumbnailTargetLocation, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Video video = new Video();
            video.setUser(user);
            video.setTitle(title);
            video.setDescription(description);
            video.setFilePath(videoFileName);
            video.setThumbnailPath(thumbnailFileName);
            video.setStatus(VideoStatus.PENDING);
            return videoRepository.save(video);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(videoTargetLocation);
            if (thumbnailTargetLocation != null) {
                Files.deleteIfExists(thumbnailTargetLocation);
            }
            throw exception;
        }
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.APPROVED);
    }

    public Optional<Video> getVideoById(Long id) {
        // Increment view count when video is accessed
        Optional<Video> videoOptional = videoRepository.findByIdAndStatus(id, VideoStatus.APPROVED);
        videoOptional.ifPresent(video -> {
            video.setViews(video.getViews() + 1);
            videoRepository.save(video);
        });
        return videoOptional;
    }

    public List<UserSubmissionResponse> getSubmissions(User user) {
        return videoRepository.findAllByUserIdOrderByUploadDateDescIdDesc(user.getId()).stream()
                .map(UserSubmissionResponse::from)
                .toList();
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
