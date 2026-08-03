package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.CompleteUploadRequest;
import com.videosharing.videosharingspringboot.dto.CancelUploadRequest;
import com.videosharing.videosharingspringboot.dto.CreateUploadSessionRequest;
import com.videosharing.videosharingspringboot.dto.UploadFileRequest;
import com.videosharing.videosharingspringboot.dto.UploadSessionResponse;
import com.videosharing.videosharingspringboot.dto.UploadTargetResponse;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.dto.UserSubmissionResponse;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;

@Service
public class VideoService {

    private final VideoRepository videoRepository;
    private final ObjectStorageService objectStorage;
    private final Path objectValidationPath;
    private final Duration presignExpiration;
    private final Duration downloadPresignExpiration;
    private final Duration rejectedDownloadRetention;
    private final Clock clock;
    private final String pendingPrefix;
    private final String publicPrefix;
    private final CategoryTagService categoryTagService;

    @Autowired
    public VideoService(VideoRepository videoRepository,
                        ObjectStorageService objectStorage,
                        CategoryTagService categoryTagService,
                        @Value("${object-storage.validation-dir:${java.io.tmpdir}/video-sharing-validation}") String objectValidationDir,
                        @Value("${object-storage.presign-expiration-minutes:15}") long presignExpirationMinutes,
                        @Value("${video-download.presign-expiration-minutes:15}") long downloadPresignExpirationMinutes,
                        @Value("${video-download.rejected-retention-days:7}") long rejectedRetentionDays,
                        @Value("${object-storage.pending-prefix:pending/}") String pendingPrefix,
                        @Value("${object-storage.public-prefix:public/}") String publicPrefix) {
        this(videoRepository, objectStorage, objectValidationDir, Duration.ofMinutes(presignExpirationMinutes),
                Duration.ofMinutes(downloadPresignExpirationMinutes), Duration.ofDays(rejectedRetentionDays),
                Clock.systemUTC(), pendingPrefix, publicPrefix, categoryTagService);
    }

    VideoService(VideoRepository videoRepository,
                 ObjectStorageService objectStorage,
                 String objectValidationDir,
                 Duration presignExpiration) {
        this(videoRepository, objectStorage, objectValidationDir, presignExpiration, presignExpiration,
                Duration.ofDays(7), Clock.systemUTC(), "pending/", "public/", null);
    }

    VideoService(VideoRepository videoRepository,
                 ObjectStorageService objectStorage,
                 String objectValidationDir,
                 Duration presignExpiration,
                 Duration rejectedDownloadRetention,
                 Clock clock) {
        this(videoRepository, objectStorage, objectValidationDir, presignExpiration, presignExpiration,
                rejectedDownloadRetention, clock, "pending/", "public/", null);
    }

    VideoService(VideoRepository videoRepository,
                 ObjectStorageService objectStorage,
                 String objectValidationDir,
                 Duration presignExpiration,
                 Duration downloadPresignExpiration,
                 Duration rejectedDownloadRetention,
                 Clock clock) {
        this(videoRepository, objectStorage, objectValidationDir, presignExpiration, downloadPresignExpiration,
                rejectedDownloadRetention, clock, "pending/", "public/", null);
    }

    VideoService(VideoRepository videoRepository,
                 ObjectStorageService objectStorage,
                 String objectValidationDir,
                 Duration presignExpiration,
                 Duration downloadPresignExpiration,
                 Duration rejectedDownloadRetention,
                 Clock clock,
                 String pendingPrefix,
                 String publicPrefix) {
        this(videoRepository, objectStorage, objectValidationDir, presignExpiration, downloadPresignExpiration,
                rejectedDownloadRetention, clock, pendingPrefix, publicPrefix, null);
    }

    VideoService(VideoRepository videoRepository,
                 ObjectStorageService objectStorage,
                 String objectValidationDir,
                 Duration presignExpiration,
                 Duration downloadPresignExpiration,
                 Duration rejectedDownloadRetention,
                 Clock clock,
                 String pendingPrefix,
                 String publicPrefix,
                 CategoryTagService categoryTagService) {
        this.videoRepository = videoRepository;
        this.objectStorage = objectStorage;
        this.objectValidationPath = Paths.get(objectValidationDir).toAbsolutePath().normalize();
        this.presignExpiration = presignExpiration;
        this.downloadPresignExpiration = downloadPresignExpiration;
        this.rejectedDownloadRetention = rejectedDownloadRetention;
        this.clock = clock;
        this.pendingPrefix = normalizePrefix(pendingPrefix);
        this.publicPrefix = normalizePrefix(publicPrefix);
        this.categoryTagService = categoryTagService;
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.APPROVED);
    }

    public Page<Video> getRecommendedVideos(Pageable pageable) {
        return videoRepository.findAllByStatusOrderByViewsDescUploadDateDescIdDesc(VideoStatus.APPROVED, pageable);
    }

    public Page<Video> getRecommendedVideosByTag(Long tagId, Pageable pageable) {
        if (tagId == null) {
            return getRecommendedVideos(pageable);
        }
        return videoRepository.findRecommendedByTag(VideoStatus.APPROVED, tagId, pageable);
    }

    public Page<Video> getRecommendedVideosByCategory(Long categoryId, Pageable pageable) {
        if (categoryId == null) {
            return getRecommendedVideos(pageable);
        }
        return videoRepository.findRecommendedByCategory(VideoStatus.APPROVED, categoryId, pageable);
    }

    public Page<Video> searchPublicVideos(String query, Pageable pageable) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return getRecommendedVideos(pageable);
        }
        return videoRepository.searchPublicVideos(VideoStatus.APPROVED, trimmed, pageable);
    }

    public Page<UserSubmissionResponse> getSubmissions(User user, Pageable pageable) {
        return videoRepository.findAllByUserIdOrderByUploadDateDescIdDesc(user.getId(), pageable)
                .map(video -> UserSubmissionResponse.from(video, now(), rejectedDownloadRetention));
    }

    public VideoDownloadTarget createDownload(User user, Long videoId, DownloadFile file) {
        requireUser(user);
        requireObjectStorage();
        Video video = videoRepository.findByIdAndUserId(videoId, user.getId())
                .orElseThrow(() -> new DownloadNotFoundException("Video not found"));
        ensureStatusAllowsDownload(video);
        String key = file == DownloadFile.VIDEO ? video.getFilePath() : video.getThumbnailPath();
        Instant deletedAt = file == DownloadFile.VIDEO ? video.getFileDeletedAt() : video.getThumbnailDeletedAt();
        if (key == null || key.isBlank()) {
            throw new DownloadNotFoundException("Download file not found");
        }
        if (deletedAt != null) {
            throw new DownloadExpiredException("Download file is no longer available");
        }
        ObjectStorageService.PresignedUpload presigned = objectStorage.presignDownload(
                key, downloadFileName(video, key), downloadPresignExpiration);
        URI url = presigned.url();
        return new VideoDownloadTarget(url.toString(), presigned.expiresAt());
    }

    public UploadSessionResponse createUploadSession(User user, CreateUploadSessionRequest request) {
        requireUser(user);
        requireObjectStorage();
        if (request == null || request.video() == null) {
            throw new IllegalArgumentException("Video upload metadata is required");
        }
        String sessionId = UUID.randomUUID().toString();
        UploadTargetResponse video = uploadTarget(user, sessionId, "video", request.video(),
                MediaFileValidator.MediaKind.VIDEO);
        UploadTargetResponse thumbnail = request.thumbnail() == null
                ? null
                : uploadTarget(user, sessionId, "thumbnail", request.thumbnail(),
                        MediaFileValidator.MediaKind.THUMBNAIL);
        return new UploadSessionResponse(sessionId, video, thumbnail,
                objectStorage.presignUpload(video.key(), video.contentType(), presignExpiration).expiresAt());
    }

    public Video completeDirectUpload(User user, CompleteUploadRequest request) throws IOException {
        requireUser(user);
        requireObjectStorage();
        if (request == null || request.uploadSessionId() == null || request.videoKey() == null) {
            throw new IllegalArgumentException("Upload completion metadata is required");
        }
        assertPendingKey(user, request.uploadSessionId(), "video", request.videoKey());
        if (request.thumbnailKey() != null && !request.thumbnailKey().isBlank()) {
            assertPendingKey(user, request.uploadSessionId(), "thumbnail", request.thumbnailKey());
        }

        MediaFileValidator.StagedMedia stagedVideo = null;
        MediaFileValidator.StagedMedia stagedThumbnail = null;
        try {
            stagedVideo = validateObject(request.videoKey(), MediaFileValidator.MediaKind.VIDEO);
            if (request.thumbnailKey() != null && !request.thumbnailKey().isBlank()) {
                stagedThumbnail = validateObject(request.thumbnailKey(), MediaFileValidator.MediaKind.THUMBNAIL);
            }
            Video video = new Video();
            video.setUser(user);
            video.setTitle(request.title());
            video.setDescription(request.description());
            video.setFilePath(request.videoKey());
            video.setThumbnailPath(request.thumbnailKey());
            applyCategoryAndTags(video, request);
            video.setStatus(VideoStatus.PENDING);
            return videoRepository.save(video);
        } catch (IOException | RuntimeException exception) {
            deleteUploadedObject(request.videoKey(), exception);
            deleteUploadedObject(request.thumbnailKey(), exception);
            throw exception;
        } finally {
            cleanup(stagedVideo == null ? null : stagedVideo.path(), new IOException("validation cleanup"));
            cleanup(stagedThumbnail == null ? null : stagedThumbnail.path(), new IOException("validation cleanup"));
        }
    }

    private void applyCategoryAndTags(Video video, CompleteUploadRequest request) {
        if (categoryTagService == null) {
            return;
        }
        CategoryTagService.Selection selection = categoryTagService.resolveUploadSelection(
                request.categoryId(), request.tagIds());
        video.setCategory(selection.category());
        video.setTags(new LinkedHashSet<>(selection.tags()));
    }

    public void cancelDirectUpload(User user, CancelUploadRequest request) {
        requireUser(user);
        requireObjectStorage();
        if (request == null || request.uploadSessionId() == null || request.videoKey() == null) {
            throw new IllegalArgumentException("Upload cancellation metadata is required");
        }
        assertPendingKey(user, request.uploadSessionId(), "video", request.videoKey());
        if (request.thumbnailKey() != null && !request.thumbnailKey().isBlank()) {
            assertPendingKey(user, request.uploadSessionId(), "thumbnail", request.thumbnailKey());
        }
        objectStorage.delete(request.videoKey());
        if (request.thumbnailKey() != null && !request.thumbnailKey().isBlank()) {
            objectStorage.delete(request.thumbnailKey());
        }
    }

    private UploadTargetResponse uploadTarget(User user, String sessionId, String slot, UploadFileRequest file,
                                              MediaFileValidator.MediaKind kind) {
        validateUploadMetadata(file, kind);
        String key = pendingPrefix + "users/" + user.getId() + "/" + sessionId + "/" + slot + "/"
                + UUID.randomUUID() + extension(file.fileName());
        var presigned = objectStorage.presignUpload(key, normalizedContentType(file.contentType()), presignExpiration);
        return new UploadTargetResponse(key, presigned.url().toString(), normalizedContentType(file.contentType()));
    }

    private MediaFileValidator.StagedMedia validateObject(String key, MediaFileValidator.MediaKind kind)
            throws IOException {
        ObjectStorageService.StoredObject object = objectStorage.get(key);
        return MediaFileValidator.stageObject(object.inputStream(), object.contentLength(), fileName(key),
                object.contentType(), objectValidationPath, kind);
    }

    private void validateUploadMetadata(UploadFileRequest file, MediaFileValidator.MediaKind kind) {
        if (file == null || file.size() <= 0) {
            throw new IllegalArgumentException("Uploaded media file must not be empty.");
        }
        if (file.size() > kind.maxBytes()) {
            throw new IllegalArgumentException("Uploaded media exceeds the " + kind.limitLabel() + " limit.");
        }
        String contentType = normalizedContentType(file.contentType());
        String extension = extension(file.fileName());
        boolean valid = kind == MediaFileValidator.MediaKind.VIDEO
                ? (contentType.equals("video/mp4") && extension.equals(".mp4"))
                        || (contentType.equals("video/webm") && extension.equals(".webm"))
                        || (contentType.equals("video/ogg") && (extension.equals(".ogv") || extension.equals(".ogg")))
                : (contentType.equals("image/jpeg") && (extension.equals(".jpg") || extension.equals(".jpeg")))
                        || (contentType.equals("image/png") && extension.equals(".png"))
                        || (contentType.equals("image/webp") && extension.equals(".webp"));
        if (!valid) {
            throw new IllegalArgumentException("Uploaded media MIME type does not match its filename.");
        }
    }

    private void assertPendingKey(User user, String sessionId, String slot, String key) {
        String expectedPrefix = pendingPrefix + "users/" + user.getId() + "/" + sessionId + "/" + slot + "/";
        if (!key.startsWith(expectedPrefix) || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Invalid upload object key");
        }
    }

    private void requireUser(User user) {
        if (user.getRole() != Role.USER) {
            throw new IllegalArgumentException("Only users may upload videos");
        }
    }

    private void requireObjectStorage() {
        if (objectStorage == null) {
            throw new IllegalStateException("Object storage is not configured");
        }
    }

    private void ensureStatusAllowsDownload(Video video) {
        if (video.getStatus() == VideoStatus.REJECTED) {
            if (video.getReviewedAt() == null || !now().isBefore(video.getReviewedAt().plus(rejectedDownloadRetention))) {
                throw new DownloadExpiredException("Rejected video download has expired");
            }
            return;
        }
        if (video.getStatus() != VideoStatus.PENDING && video.getStatus() != VideoStatus.APPROVED) {
            throw new DownloadExpiredException("Video is not available for download");
        }
    }

    private static String downloadFileName(Video video, String key) {
        String name = fileName(key);
        if (name == null || name.isBlank()) {
            return "video-" + video.getId();
        }
        return name;
    }

    private static String fileName(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    private static String extension(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dot = baseName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return baseName.substring(dot);
    }

    private static String normalizedContentType(String contentType) {
        return contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
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

    private void deleteUploadedObject(String key, Exception original) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            objectStorage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private Instant now() {
        return clock.instant();
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    public enum DownloadFile {
        VIDEO,
        THUMBNAIL
    }

    public record VideoDownloadTarget(String url, Instant expiresAt) {
    }

    public static class DownloadNotFoundException extends RuntimeException {
        public DownloadNotFoundException(String message) {
            super(message);
        }
    }

    public static class DownloadExpiredException extends RuntimeException {
        public DownloadExpiredException(String message) {
            super(message);
        }
    }
}
