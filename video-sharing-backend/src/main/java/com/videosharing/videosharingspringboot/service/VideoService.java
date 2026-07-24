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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        MediaFormat videoFormat = detectVideoFormat(videoFile);
        MediaFormat thumbnailFormat = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            thumbnailFormat = detectThumbnailFormat(thumbnailFile);
        }

        String videoFileName = UUID.randomUUID() + videoFormat.serverExtension();
        Path videoTargetLocation = pendingUploadPath.resolve(videoFileName);
        String thumbnailFileName = null;
        Path thumbnailTargetLocation = null;
        if (thumbnailFormat != null) {
            thumbnailFileName = UUID.randomUUID() + thumbnailFormat.serverExtension();
            thumbnailTargetLocation = pendingThumbnailPath.resolve(thumbnailFileName);
        }

        try {
            Files.createDirectories(pendingUploadPath);
            copy(videoFile, videoTargetLocation);
            if (thumbnailTargetLocation != null) {
                Files.createDirectories(pendingThumbnailPath);
                copy(thumbnailFile, thumbnailTargetLocation);
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
            cleanup(videoTargetLocation, exception);
            cleanup(thumbnailTargetLocation, exception);
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

    private static void copy(MultipartFile file, Path target) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
        }
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

    private static MediaFormat detectVideoFormat(MultipartFile file) throws IOException {
        byte[] content = content(file);
        MediaFormat format;
        if (matches(content, 4, "ftyp".getBytes(StandardCharsets.US_ASCII))) {
            format = MediaFormat.MP4;
        } else if (matches(content, 0, bytes(0x1A, 0x45, 0xDF, 0xA3))) {
            format = MediaFormat.WEBM;
        } else if (matches(content, 0, "OggS".getBytes(StandardCharsets.US_ASCII))) {
            format = MediaFormat.OGV;
        } else {
            throw new IllegalArgumentException("Unsupported or truncated video file signature.");
        }
        validateMetadata(file, format);
        rejectActiveContent(content);
        return format;
    }

    private static MediaFormat detectThumbnailFormat(MultipartFile file) throws IOException {
        byte[] content = content(file);
        MediaFormat format;
        if (matches(content, 0, bytes(0xFF, 0xD8, 0xFF))) {
            format = MediaFormat.JPEG;
        } else if (matches(content, 0, bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
            format = MediaFormat.PNG;
        } else if (matches(content, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && matches(content, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            format = MediaFormat.WEBP;
        } else {
            throw new IllegalArgumentException("Unsupported or truncated thumbnail file signature.");
        }
        validateMetadata(file, format);
        rejectActiveContent(content);
        return format;
    }

    private static byte[] content(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded media file must not be empty.");
        }
        return file.getBytes();
    }

    private static void validateMetadata(MultipartFile file, MediaFormat format) {
        String contentType = file.getContentType();
        String normalizedType = contentType == null
                ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!format.mimeTypes().contains(normalizedType)) {
            throw new IllegalArgumentException("Uploaded media MIME type does not match its content.");
        }

        String original = file.getOriginalFilename();
        String normalizedName = original == null ? "" : original.replace('\\', '/').toLowerCase(Locale.ROOT);
        int slash = normalizedName.lastIndexOf('/');
        String baseName = normalizedName.substring(slash + 1);
        if (format.sourceExtensions().stream().noneMatch(baseName::endsWith)) {
            throw new IllegalArgumentException("Uploaded media filename extension does not match its content.");
        }
    }

    private static void rejectActiveContent(byte[] content) {
        if (matches(content, 0, bytes('M', 'Z'))
                || matches(content, 0, bytes(0x7F, 'E', 'L', 'F'))
                || matches(content, 0, bytes('#', '!'))) {
            throw new IllegalArgumentException("Executable media content is not allowed.");
        }
        for (String marker : List.of("<html", "<script", "<svg", "<!doctype", "<?xml", "javascript:")) {
            if (containsAsciiIgnoreCase(content, marker)) {
                throw new IllegalArgumentException("Active or polyglot media content is not allowed.");
            }
        }
    }

    private static boolean containsAsciiIgnoreCase(byte[] content, String marker) {
        byte[] expected = marker.getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= content.length - expected.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < expected.length; index++) {
                int actual = content[offset + index] & 0xFF;
                int wanted = expected[index] & 0xFF;
                if (actual >= 'A' && actual <= 'Z') {
                    actual += 'a' - 'A';
                }
                if (actual != wanted) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(byte[] content, int offset, byte[] expected) {
        if (offset < 0 || content.length - offset < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private enum MediaFormat {
        MP4(".mp4", Set.of("video/mp4"), Set.of(".mp4")),
        WEBM(".webm", Set.of("video/webm"), Set.of(".webm")),
        OGV(".ogv", Set.of("video/ogg"), Set.of(".ogv", ".ogg")),
        JPEG(".jpg", Set.of("image/jpeg"), Set.of(".jpg", ".jpeg")),
        PNG(".png", Set.of("image/png"), Set.of(".png")),
        WEBP(".webp", Set.of("image/webp"), Set.of(".webp"));

        private final String serverExtension;
        private final Set<String> mimeTypes;
        private final Set<String> sourceExtensions;

        MediaFormat(String serverExtension, Set<String> mimeTypes, Set<String> sourceExtensions) {
            this.serverExtension = serverExtension;
            this.mimeTypes = mimeTypes;
            this.sourceExtensions = sourceExtensions;
        }

        String serverExtension() {
            return serverExtension;
        }

        Set<String> mimeTypes() {
            return mimeTypes;
        }

        Set<String> sourceExtensions() {
            return sourceExtensions;
        }
    }
}
