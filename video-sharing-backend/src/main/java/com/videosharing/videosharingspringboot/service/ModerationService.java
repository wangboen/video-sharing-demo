package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionResponse;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ModerationService {
    private static final Duration STAFF_HEARTBEAT_INTERVAL = Duration.ofSeconds(60);

    private final VideoRepository videos;
    private final StaffAccountRepository staffAccounts;
    private final ObjectStorageService objectStorage;
    private final Path objectValidationPath;
    private final Duration leaseDuration;
    private final Clock clock;
    private final TransactionOperations decisionTransactions;
    private final String pendingPrefix;
    private final String publicPrefix;

    @Autowired
    public ModerationService(VideoRepository videos,
                             StaffAccountRepository staffAccounts,
                             @Value("${moderation.lease-duration-minutes:15}") long leaseMinutes,
                              ObjectStorageService objectStorage,
                              @Value("${object-storage.validation-dir:${java.io.tmpdir}/video-sharing-validation}") String objectValidationDir,
                              @Value("${object-storage.public-base-url:}") String objectStoragePublicBaseUrl,
                              @Value("${object-storage.pending-prefix:pending/}") String pendingPrefix,
                              @Value("${object-storage.public-prefix:public/}") String publicPrefix,
                              PlatformTransactionManager transactionManager) {
        this(videos, staffAccounts, Duration.ofMinutes(leaseMinutes), Clock.systemUTC(),
                decisionTransactionTemplate(transactionManager), objectStorage, Paths.get(objectValidationDir),
                objectStoragePublicBaseUrl, pendingPrefix, publicPrefix);
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock) {
        this(videos, staffAccounts, leaseDuration, clock, TransactionOperations.withoutTransaction(), null,
                Paths.get(System.getProperty("java.io.tmpdir")).resolve("video-sharing-validation"), "",
                "pending/", "public/");
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock,
                      ObjectStorageService objectStorage,
                      Path objectValidationPath) {
        this(videos, staffAccounts, leaseDuration, clock, TransactionOperations.withoutTransaction(), objectStorage,
                objectValidationPath, "", "pending/", "public/");
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock,
                      ObjectStorageService objectStorage,
                      Path objectValidationPath,
                      String objectStoragePublicBaseUrl) {
        this(videos, staffAccounts, leaseDuration, clock, TransactionOperations.withoutTransaction(), objectStorage,
                objectValidationPath, objectStoragePublicBaseUrl, "pending/", "public/");
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock,
                      ObjectStorageService objectStorage,
                      Path objectValidationPath,
                      String objectStoragePublicBaseUrl,
                      String pendingPrefix,
                      String publicPrefix) {
        this(videos, staffAccounts, leaseDuration, clock, TransactionOperations.withoutTransaction(), objectStorage,
                objectValidationPath, objectStoragePublicBaseUrl, pendingPrefix, publicPrefix);
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock,
                      TransactionOperations decisionTransactions) {
        this(videos, staffAccounts, leaseDuration, clock, decisionTransactions, null,
                Paths.get(System.getProperty("java.io.tmpdir")).resolve("video-sharing-validation"), "",
                "pending/", "public/");
    }

    ModerationService(VideoRepository videos,
                      StaffAccountRepository staffAccounts,
                      Duration leaseDuration,
                      Clock clock,
                      TransactionOperations decisionTransactions,
                      ObjectStorageService objectStorage,
                      Path objectValidationPath,
                      String objectStoragePublicBaseUrl,
                      String pendingPrefix,
                      String publicPrefix) {
        if (leaseDuration == null || leaseDuration.compareTo(STAFF_HEARTBEAT_INTERVAL) <= 0) {
            throw new IllegalArgumentException(
                    "Moderation lease duration must be greater than the 60-second staff heartbeat interval");
        }
        this.videos = videos;
        this.staffAccounts = staffAccounts;
        this.objectStorage = objectStorage;
        this.objectValidationPath = normalize(objectValidationPath);
        this.leaseDuration = leaseDuration;
        this.clock = clock;
        this.decisionTransactions = Objects.requireNonNull(decisionTransactions);
        this.pendingPrefix = normalizePrefix(pendingPrefix);
        this.publicPrefix = normalizePrefix(publicPrefix);
    }

    @Transactional
    public Optional<ModerationVideoResponse> claimNext(String username) {
        Instant now = now();
        StaffAccount owner = requireStaffForUpdate(username);

        Optional<Video> existing = videos.findActiveLeaseForOwner(owner.getId(), VideoStatus.PENDING, now);
        if (existing.isPresent()) {
            return existing.map(this::moderationResponse);
        }

        List<Video> available = videos.findClaimableForUpdate(VideoStatus.PENDING, now, PageRequest.of(0, 1));
        if (available.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(lease(available.get(0), owner, now));
    }

    @Transactional(readOnly = true)
    public List<ModerationVideoResponse> queue(String username, int limit) {
        Instant now = now();
        StaffAccount owner = requireStaff(username);
        int normalizedLimit = normalizeQueueLimit(limit);
        Optional<Video> activeLease = videos.findActiveLeaseForOwner(owner.getId(), VideoStatus.PENDING, now);
        int claimableLimit = activeLease.isPresent() ? normalizedLimit - 1 : normalizedLimit;
        List<ModerationVideoResponse> result = activeLease.stream()
                .map(ModerationVideoResponse::from)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (claimableLimit > 0) {
            videos.findClaimable(VideoStatus.PENDING, now, PageRequest.of(0, claimableLimit)).stream()
                    .map(ModerationVideoResponse::from)
                    .forEach(result::add);
        }
        return result;
    }

    @Transactional
    public ModerationVideoResponse claim(String username, Long id) {
        Instant now = now();
        StaffAccount owner = requireStaffForUpdate(username);
        Video video = locked(id);
        if (video.getStatus() != VideoStatus.PENDING) {
            throw new ModerationConflictException("Video is not pending moderation");
        }
        if (video.getLeaseOwner() != null && video.getLeaseExpiresAt() != null
                && video.getLeaseExpiresAt().isAfter(now)) {
            if (video.getLeaseOwner().getUsername().equals(username)) {
                return moderationResponse(video);
            }
            throw new ModerationConflictException("Video is already leased by another reviewer");
        }
        return lease(video, owner, now);
    }

    @Transactional
    public ModerationVideoResponse heartbeat(Long id, String username) {
        Video video = locked(id);
        assertActiveOwner(video, username);
        video.setLeaseExpiresAt(now().plus(leaseDuration));
        videos.save(video);
        videos.flush();
        return moderationResponse(video);
    }

    @Transactional
    public void release(Long id, String username, boolean force, Role callerRole) {
        Video video = locked(id);
        if (callerRole != Role.REVIEWER) {
            throw new AccessDeniedException("Reviewer role required");
        }
        if (force) {
            throw new AccessDeniedException("Force release is not available");
        } else if (video.getLeaseOwner() == null
                || !video.getLeaseOwner().getUsername().equals(username)) {
            throw new ModerationConflictException("The caller does not own this lease");
        }
        clearLease(video);
        videos.save(video);
    }

    public ModerationDecisionResponse decide(Long id, String username, ModerationDecisionRequest request) {
        ApprovalAttempt attempt = new ApprovalAttempt();
        try {
            ModerationDecisionResponse response = decisionTransactions.execute(
                    transaction -> decideInTransaction(id, username, request, attempt));
            if (response == null) {
                throw new IllegalStateException("Moderation decision transaction returned no result");
            }
            if (attempt.started()) {
                deleteObject(attempt.filePath);
                deleteObject(attempt.thumbnailPath);
            }
            return response;
        } catch (RuntimeException exception) {
            if (!attempt.started()) {
                throw exception;
            }
            Throwable original = exception instanceof MediaPublicationFailure
                    && exception.getCause() != null ? exception.getCause() : exception;
            attempt.reset();
            String detail = original.getMessage() == null
                    ? original.getClass().getSimpleName()
                    : original.getMessage();
            String message = "Could not publish approved media: " + detail;
            ModerationConflictException failure = new ModerationConflictException(message, original);
            throw failure;
        }
    }

    private ModerationDecisionResponse decideInTransaction(Long id,
                                                           String username,
                                                           ModerationDecisionRequest request,
                                                           ApprovalAttempt attempt) {
        Video video = locked(id);
        if (video.getStatus() != VideoStatus.PENDING) {
            throw new ModerationConflictException("Video has already been decided");
        }
        assertActiveOwner(video, username);
        if (!Objects.equals(video.getVersion(), request.version())) {
            throw new ModerationConflictException("Video version is stale");
        }

        String decision = request.decision().trim().toUpperCase();
        if ("REJECT".equals(decision)) {
            reject(video, request.reason());
            return ModerationDecisionResponse.from(videos.save(video));
        }
        if (!"APPROVE".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
        }
        attempt.capture(video);
        approve(video);
        return ModerationDecisionResponse.from(video);
    }

    private void reject(Video video, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Rejection reason must not exceed 500 characters");
        }
        video.setStatus(VideoStatus.REJECTED);
        video.setReviewer(video.getLeaseOwner());
        video.setReviewedAt(now());
        video.setRejectionReason(trimmed);
        clearLease(video);
    }

    private void approve(Video video) {
        approveObjectStorage(video);
    }

    private void approveObjectStorage(Video video) {
        MediaFileValidator.StagedMedia stagedVideo = null;
        MediaFileValidator.StagedMedia stagedThumbnail = null;
        try {
            String pendingVideoName = video.getFilePath();
            String pendingThumbnailName = video.getThumbnailPath();
            assertPendingKey(pendingVideoName);
            if (pendingThumbnailName != null) {
                assertPendingKey(pendingThumbnailName);
            }
            stagedVideo = validateObject(pendingVideoName, MediaFileValidator.MediaKind.VIDEO);
            stagedThumbnail = pendingThumbnailName == null
                    ? null
                    : validateObject(pendingThumbnailName, MediaFileValidator.MediaKind.THUMBNAIL);
            String publishedVideoName = publishedObjectKey(video, "video", stagedVideo.format());
            String publishedThumbnailName = stagedThumbnail == null
                    ? null
                    : publishedObjectKey(video, "thumbnail", stagedThumbnail.format());
            objectStorage.copy(pendingVideoName, publishedVideoName, stagedVideo.format().mediaType());
            if (stagedThumbnail != null) {
                objectStorage.copy(pendingThumbnailName, publishedThumbnailName,
                        stagedThumbnail.format().mediaType());
            }
            video.setFilePath(publishedVideoName);
            video.setThumbnailPath(publishedThumbnailName);
            video.setStatus(VideoStatus.APPROVED);
            video.setReviewer(video.getLeaseOwner());
            video.setReviewedAt(now());
            video.setRejectionReason(null);
            clearLease(video);
            videos.saveAndFlush(video);
        } catch (IOException exception) {
            throw new MediaPublicationFailure(exception);
        } finally {
            cleanup(stagedVideo == null ? null : stagedVideo.path());
            cleanup(stagedThumbnail == null ? null : stagedThumbnail.path());
        }
    }

    private MediaFileValidator.StagedMedia validateObject(String key, MediaFileValidator.MediaKind kind)
            throws IOException {
        ObjectStorageService.StoredObject object = objectStorage.get(key);
        return MediaFileValidator.stageObject(object.inputStream(), object.contentLength(), fileName(key),
                object.contentType(), objectValidationPath, kind);
    }

    private String publishedObjectKey(Video video, String slot, MediaFileValidator.MediaFormat format) {
        Long userId = video.getUser() == null ? 0L : video.getUser().getId();
        return publicPrefix + "users/" + userId + "/" + slot + "/" + UUID.randomUUID() + format.serverExtension();
    }

    private void deleteObject(String key) {
        if (objectStorage != null && key != null && !key.isBlank()) {
            objectStorage.delete(key);
        }
    }

    private void assertPendingKey(String key) {
        if (key == null || !key.startsWith(pendingPrefix) || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Invalid pending object key");
        }
    }

    private static String fileName(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    private static void cleanup(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private Video locked(Long id) {
        return videos.findByIdForUpdate(id)
                .orElseThrow(() -> new ModerationConflictException("Video not found"));
    }

    private StaffAccount requireStaff(String username) {
        StaffAccount staff = staffAccounts.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Staff account not found"));
        assertStaffRole(staff);
        return staff;
    }

    private StaffAccount requireStaffForUpdate(String username) {
        StaffAccount staff = staffAccounts.findByUsernameForUpdate(username)
                .orElseThrow(() -> new AccessDeniedException("Staff account not found"));
        assertStaffRole(staff);
        return staff;
    }

    private void assertStaffRole(StaffAccount staff) {
        if (staff.getRole() != Role.REVIEWER) {
            throw new AccessDeniedException("Reviewer role required");
        }
    }

    private ModerationVideoResponse lease(Video video, StaffAccount owner, Instant now) {
        video.setLeaseOwner(owner);
        video.setClaimedAt(now);
        video.setLeaseExpiresAt(now.plus(leaseDuration));
        videos.save(video);
        videos.flush();
        return moderationResponse(video);
    }

    private ModerationVideoResponse moderationResponse(Video video) {
        String videoUrl = presignedPreviewUrl(video.getFilePath());
        String thumbnailUrl = video.getThumbnailPath() == null ? null : presignedPreviewUrl(video.getThumbnailPath());
        return ModerationVideoResponse.from(video, videoUrl, thumbnailUrl);
    }

    private String presignedPreviewUrl(String key) {
        if (objectStorage == null || key == null || key.isBlank()) {
            return null;
        }
        String name = fileName(key);
        return objectStorage.presignDownload(key, name, leaseDuration).url().toString();
    }

    private void assertActiveOwner(Video video, String username) {
        Instant now = now();
        if (video.getStatus() != VideoStatus.PENDING
                || video.getLeaseOwner() == null
                || !video.getLeaseOwner().getUsername().equals(username)
                || video.getLeaseExpiresAt() == null
                || !video.getLeaseExpiresAt().isAfter(now)) {
            throw new ModerationConflictException("An active lease owned by the caller is required");
        }
    }

    private void clearLease(Video video) {
        video.setLeaseOwner(null);
        video.setClaimedAt(null);
        video.setLeaseExpiresAt(null);
    }

    private Instant now() {
        return clock.instant();
    }

    private static int normalizeQueueLimit(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static TransactionTemplate decisionTransactionTemplate(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static final class ApprovalAttempt {
        private Video video;
        private VideoStatus status;
        private Long version;
        private String filePath;
        private String thumbnailPath;
        private StaffAccount reviewer;
        private Instant reviewedAt;
        private String rejectionReason;
        private StaffAccount leaseOwner;
        private Instant claimedAt;
        private Instant leaseExpiresAt;

        void capture(Video video) {
            this.video = video;
            status = video.getStatus();
            version = video.getVersion();
            filePath = video.getFilePath();
            thumbnailPath = video.getThumbnailPath();
            reviewer = video.getReviewer();
            reviewedAt = video.getReviewedAt();
            rejectionReason = video.getRejectionReason();
            leaseOwner = video.getLeaseOwner();
            claimedAt = video.getClaimedAt();
            leaseExpiresAt = video.getLeaseExpiresAt();
        }

        boolean started() {
            return video != null;
        }

        void reset() {
            video.setStatus(status);
            video.setVersion(version);
            video.setFilePath(filePath);
            video.setThumbnailPath(thumbnailPath);
            video.setReviewer(reviewer);
            video.setReviewedAt(reviewedAt);
            video.setRejectionReason(rejectionReason);
            video.setLeaseOwner(leaseOwner);
            video.setClaimedAt(claimedAt);
            video.setLeaseExpiresAt(leaseExpiresAt);
        }
    }

    private static final class MediaPublicationFailure extends RuntimeException {
        private MediaPublicationFailure(IOException cause) {
            super(cause);
        }
    }

}
