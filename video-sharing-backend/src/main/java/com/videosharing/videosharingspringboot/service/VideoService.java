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
import java.util.List;
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
        MediaFileValidator.StagedMedia stagedVideo = null;
        MediaFileValidator.StagedMedia stagedThumbnail = null;
        Path videoTargetLocation = null;
        Path thumbnailTargetLocation = null;
        String videoFileName = null;
        String thumbnailFileName = null;

        try {
            stagedVideo = MediaFileValidator.stage(
                    videoFile, pendingUploadPath, MediaFileValidator.MediaKind.VIDEO);
            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                stagedThumbnail = MediaFileValidator.stage(
                        thumbnailFile, pendingThumbnailPath, MediaFileValidator.MediaKind.THUMBNAIL);
            }

            videoFileName = UUID.randomUUID() + stagedVideo.format().serverExtension();
            videoTargetLocation = pendingUploadPath.resolve(videoFileName);
            if (stagedThumbnail != null) {
                thumbnailFileName = UUID.randomUUID() + stagedThumbnail.format().serverExtension();
                thumbnailTargetLocation = pendingThumbnailPath.resolve(thumbnailFileName);
            }

            MediaFileValidator.moveAtomically(stagedVideo.path(), videoTargetLocation);
            stagedVideo = null;
            if (stagedThumbnail != null) {
                MediaFileValidator.moveAtomically(stagedThumbnail.path(), thumbnailTargetLocation);
                stagedThumbnail = null;
            }

            Video video = new Video();
            video.setUser(user);
            video.setTitle(title);
            video.setDescription(description);
            video.setFilePath(videoFileName);
            video.setThumbnailPath(thumbnailFileName);
            video.setStatus(VideoStatus.PENDING);
            return videoRepository.save(video);
        } catch (IOException | RuntimeException exception) {
            cleanup(stagedVideo == null ? null : stagedVideo.path(), exception);
            cleanup(stagedThumbnail == null ? null : stagedThumbnail.path(), exception);
            cleanup(videoTargetLocation, exception);
            cleanup(thumbnailTargetLocation, exception);
            throw exception;
        }
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.APPROVED);
    }

    public List<UserSubmissionResponse> getSubmissions(User user) {
        return videoRepository.findAllByUserIdOrderByUploadDateDescIdDesc(user.getId()).stream()
                .map(UserSubmissionResponse::from)
                .toList();
    }

    private static void cleanup(Path target, Exception original) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }
}
