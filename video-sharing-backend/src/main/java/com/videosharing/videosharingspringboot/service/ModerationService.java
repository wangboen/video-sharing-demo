package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionResponse;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ModerationService {
    private static final Duration STAFF_HEARTBEAT_INTERVAL = Duration.ofSeconds(60);

    private final VideoRepository videos;
    private final UserRepository users;
    private final Path pendingVideos;
    private final Path pendingThumbnails;
    private final Path publicVideos;
    private final Path publicThumbnails;
    private final Duration leaseDuration;
    private final Clock clock;
    private final TransactionOperations decisionTransactions;

    @Autowired
    public ModerationService(VideoRepository videos,
                             UserRepository users,
                             @Value("${file.pending-upload-dir}") String pendingVideos,
                             @Value("${file.pending-thumbnail-dir}") String pendingThumbnails,
                             @Value("${file.upload-dir}") String publicVideos,
                             @Value("${file.thumbnail-dir}") String publicThumbnails,
                             @Value("${moderation.lease-duration-minutes:15}") long leaseMinutes,
                             PlatformTransactionManager transactionManager) {
        this(videos, users, Paths.get(pendingVideos), Paths.get(pendingThumbnails),
                Paths.get(publicVideos), Paths.get(publicThumbnails),
                Duration.ofMinutes(leaseMinutes), Clock.systemUTC(),
                decisionTransactionTemplate(transactionManager));
    }

    ModerationService(VideoRepository videos,
                      UserRepository users,
                      Path pendingVideos,
                      Path pendingThumbnails,
                      Path publicVideos,
                      Path publicThumbnails,
                      Duration leaseDuration,
                      Clock clock) {
        this(videos, users, pendingVideos, pendingThumbnails, publicVideos, publicThumbnails,
                leaseDuration, clock, TransactionOperations.withoutTransaction());
    }

    ModerationService(VideoRepository videos,
                      UserRepository users,
                      Path pendingVideos,
                      Path pendingThumbnails,
                      Path publicVideos,
                      Path publicThumbnails,
                      Duration leaseDuration,
                      Clock clock,
                      TransactionOperations decisionTransactions) {
        if (leaseDuration == null || leaseDuration.compareTo(STAFF_HEARTBEAT_INTERVAL) <= 0) {
            throw new IllegalArgumentException(
                    "Moderation lease duration must be greater than the 60-second staff heartbeat interval");
        }
        this.videos = videos;
        this.users = users;
        this.pendingVideos = normalize(pendingVideos);
        this.pendingThumbnails = normalize(pendingThumbnails);
        this.publicVideos = normalize(publicVideos);
        this.publicThumbnails = normalize(publicThumbnails);
        this.leaseDuration = leaseDuration;
        this.clock = clock;
        this.decisionTransactions = Objects.requireNonNull(decisionTransactions);
    }

    @Transactional
    public Optional<ModerationVideoResponse> claimNext(String username) {
        Instant now = now();
        User owner = users.findByUsernameForUpdate(username)
                .orElseThrow(() -> new AccessDeniedException("Staff account not found"));
        if (owner.getRole() != Role.REVIEWER && owner.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Staff role required");
        }

        Optional<Video> existing = videos.findActiveLeaseForOwner(owner.getId(), VideoStatus.PENDING, now);
        if (existing.isPresent()) {
            return existing.map(ModerationVideoResponse::from);
        }

        List<Video> available = videos.findClaimableForUpdate(VideoStatus.PENDING, now, PageRequest.of(0, 1));
        if (available.isEmpty()) {
            return Optional.empty();
        }
        Video video = available.get(0);
        video.setLeaseOwner(owner);
        video.setClaimedAt(now);
        video.setLeaseExpiresAt(now.plus(leaseDuration));
        videos.save(video);
        videos.flush();
        return Optional.of(ModerationVideoResponse.from(video));
    }

    @Transactional
    public ModerationVideoResponse heartbeat(Long id, String username) {
        Video video = locked(id);
        assertActiveOwner(video, username);
        video.setLeaseExpiresAt(now().plus(leaseDuration));
        videos.save(video);
        videos.flush();
        return ModerationVideoResponse.from(video);
    }

    @Transactional
    public void release(Long id, String username, boolean force, Role callerRole) {
        Video video = locked(id);
        if (force) {
            if (callerRole != Role.ADMIN) {
                throw new AccessDeniedException("Only admins may force release a lease");
            }
        } else if (video.getLeaseOwner() == null
                || !video.getLeaseOwner().getUsername().equals(username)) {
            throw new ModerationConflictException("The caller does not own this lease");
        }
        clearLease(video);
        videos.save(video);
    }

    @Transactional(readOnly = true)
    public ModerationPreview preview(Long id, String username, boolean thumbnail) {
        Video video = videos.findById(id)
                .orElseThrow(() -> new ModerationConflictException("Video not found"));
        assertActiveOwner(video, username);
        String fileName = thumbnail ? video.getThumbnailPath() : video.getFilePath();
        if (fileName == null) {
            throw new PreviewNotFoundException("Preview not found");
        }
        Path path = safeResolve(thumbnail ? pendingThumbnails : pendingVideos, fileName);
        if (!Files.isRegularFile(path)) {
            throw new PreviewNotFoundException("Preview not found");
        }
        try {
            MediaFileValidator.MediaFormat format = MediaFileValidator.inspect(
                    path, thumbnail
                            ? MediaFileValidator.MediaKind.THUMBNAIL
                            : MediaFileValidator.MediaKind.VIDEO);
            return new ModerationPreview(
                    new PathResource(path),
                    MediaType.parseMediaType(format.mediaType()),
                    "preview" + format.serverExtension());
        } catch (IOException | IllegalArgumentException exception) {
            throw new PreviewNotFoundException("Preview not found");
        }
    }

    public ModerationDecisionResponse decide(Long id, String username, ModerationDecisionRequest request) {
        List<Move> completed = new ArrayList<>();
        ApprovalAttempt attempt = new ApprovalAttempt();
        try {
            ModerationDecisionResponse response = decisionTransactions.execute(
                    transaction -> decideInTransaction(id, username, request, completed, attempt));
            if (response == null) {
                throw new IllegalStateException("Moderation decision transaction returned no result");
            }
            return response;
        } catch (RuntimeException exception) {
            if (!attempt.started()) {
                throw exception;
            }
            Throwable original = exception instanceof MediaPublicationFailure
                    && exception.getCause() != null ? exception.getCause() : exception;
            IOException compensationFailure = null;
            try {
                restore(completed);
            } catch (IOException restoreFailure) {
                compensationFailure = restoreFailure;
            }
            attempt.reset();
            String detail = original.getMessage() == null
                    ? original.getClass().getSimpleName()
                    : original.getMessage();
            String message = compensationFailure == null
                    ? "Could not publish approved media: " + detail
                    : "Could not publish approved media and compensation failed: " + detail;
            ModerationConflictException failure = new ModerationConflictException(message, original);
            if (compensationFailure != null) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }

    private ModerationDecisionResponse decideInTransaction(Long id,
                                                           String username,
                                                           ModerationDecisionRequest request,
                                                           List<Move> completed,
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
        approve(video, completed);
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

    private void approve(Video video, List<Move> completed) {
        try {
            Path pendingVideo = safeResolve(pendingVideos, video.getFilePath());
            MediaFileValidator.MediaFormat videoFormat = MediaFileValidator.inspect(
                    pendingVideo, MediaFileValidator.MediaKind.VIDEO);
            Path pendingThumbnail = null;
            MediaFileValidator.MediaFormat thumbnailFormat = null;
            if (video.getThumbnailPath() != null) {
                pendingThumbnail = safeResolve(pendingThumbnails, video.getThumbnailPath());
                thumbnailFormat = MediaFileValidator.inspect(
                        pendingThumbnail, MediaFileValidator.MediaKind.THUMBNAIL);
            }

            String publishedVideoName = UUID.randomUUID() + videoFormat.serverExtension();
            String publishedThumbnailName = thumbnailFormat == null
                    ? null
                    : UUID.randomUUID() + thumbnailFormat.serverExtension();
            move(pendingVideo, safeResolve(publicVideos, publishedVideoName), completed);
            if (pendingThumbnail != null) {
                move(pendingThumbnail, safeResolve(publicThumbnails, publishedThumbnailName), completed);
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
        }
    }

    private void move(Path source, Path target, List<Move> completed) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) {
            throw new IOException("Unsafe media source/destination state for " + source.getFileName());
        }
        Files.createDirectories(target.getParent());
        MediaFileValidator.moveAtomically(source, target);
        completed.add(new Move(source, target));
    }

    private void restore(List<Move> completed) throws IOException {
        IOException firstFailure = null;
        for (int index = completed.size() - 1; index >= 0; index--) {
            Move move = completed.get(index);
            try {
                Files.createDirectories(move.source().getParent());
                boolean targetExists = Files.isRegularFile(move.target());
                boolean sourceExists = Files.exists(move.source());
                if (!targetExists || sourceExists) {
                    throw new IOException("Unsafe compensation state for " + move.source().getFileName()
                            + ": approved target must exist and pending source must be absent");
                }
                Files.move(move.target(), move.source());
            } catch (IOException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private Video locked(Long id) {
        return videos.findByIdForUpdate(id)
                .orElseThrow(() -> new ModerationConflictException("Video not found"));
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

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path safeResolve(Path directory, String fileName) {
        Path resolved = directory.resolve(fileName).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return resolved;
    }

    private static TransactionTemplate decisionTransactionTemplate(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private record Move(Path source, Path target) {
    }

    private static final class ApprovalAttempt {
        private Video video;
        private VideoStatus status;
        private Long version;
        private String filePath;
        private String thumbnailPath;
        private User reviewer;
        private Instant reviewedAt;
        private String rejectionReason;
        private User leaseOwner;
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

    public static class PreviewNotFoundException extends RuntimeException {
        public PreviewNotFoundException(String message) {
            super(message);
        }
    }
}
