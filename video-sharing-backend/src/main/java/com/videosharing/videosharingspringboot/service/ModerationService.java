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
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ModerationService {
    private final VideoRepository videos;
    private final UserRepository users;
    private final Path pendingVideos;
    private final Path pendingThumbnails;
    private final Path publicVideos;
    private final Path publicThumbnails;
    private final Duration leaseDuration;
    private final Clock clock;

    @Autowired
    public ModerationService(VideoRepository videos,
                             UserRepository users,
                             @Value("${file.pending-upload-dir}") String pendingVideos,
                             @Value("${file.pending-thumbnail-dir}") String pendingThumbnails,
                             @Value("${file.upload-dir}") String publicVideos,
                             @Value("${file.thumbnail-dir}") String publicThumbnails,
                             @Value("${moderation.lease-duration-minutes:15}") long leaseMinutes) {
        this(videos, users, Paths.get(pendingVideos), Paths.get(pendingThumbnails),
                Paths.get(publicVideos), Paths.get(publicThumbnails),
                Duration.ofMinutes(leaseMinutes), Clock.systemUTC());
    }

    ModerationService(VideoRepository videos,
                      UserRepository users,
                      Path pendingVideos,
                      Path pendingThumbnails,
                      Path publicVideos,
                      Path publicThumbnails,
                      Duration leaseDuration,
                      Clock clock) {
        this.videos = videos;
        this.users = users;
        this.pendingVideos = normalize(pendingVideos);
        this.pendingThumbnails = normalize(pendingThumbnails);
        this.publicVideos = normalize(publicVideos);
        this.publicThumbnails = normalize(publicThumbnails);
        this.leaseDuration = leaseDuration;
        this.clock = clock;
    }

    @Transactional
    public Optional<ModerationVideoResponse> claimNext(String username) {
        LocalDateTime now = now();
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
        return Optional.of(ModerationVideoResponse.from(videos.save(video)));
    }

    @Transactional
    public ModerationVideoResponse heartbeat(Long id, String username) {
        Video video = locked(id);
        assertActiveOwner(video, username);
        video.setLeaseExpiresAt(now().plus(leaseDuration));
        return ModerationVideoResponse.from(videos.save(video));
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
    public Resource preview(Long id, String username, boolean thumbnail) {
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
        return new PathResource(path);
    }

    @Transactional
    public ModerationDecisionResponse decide(Long id, String username, ModerationDecisionRequest request) {
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
        List<Move> completed = new ArrayList<>();
        try {
            move(pendingVideos, publicVideos, video.getFilePath(), completed);
            if (video.getThumbnailPath() != null) {
                move(pendingThumbnails, publicThumbnails, video.getThumbnailPath(), completed);
            }
            video.setStatus(VideoStatus.APPROVED);
            video.setReviewer(video.getLeaseOwner());
            video.setReviewedAt(now());
            video.setRejectionReason(null);
            clearLease(video);
            videos.saveAndFlush(video);
            restoreOnTransactionRollback(completed);
        } catch (IOException | RuntimeException exception) {
            restore(completed);
            video.setStatus(VideoStatus.PENDING);
            video.setReviewer(null);
            video.setReviewedAt(null);
            video.setRejectionReason(null);
            throw new ModerationConflictException("Could not publish approved media: " + exception.getMessage());
        }
    }

    private void move(Path sourceDirectory, Path targetDirectory, String fileName, List<Move> completed)
            throws IOException {
        Path source = safeResolve(sourceDirectory, fileName);
        Path target = safeResolve(targetDirectory, fileName);
        if (!Files.isRegularFile(source) || Files.exists(target)) {
            throw new IOException("Unsafe media source/destination state for " + fileName);
        }
        Files.createDirectories(targetDirectory);
        Files.move(source, target);
        completed.add(new Move(source, target));
    }

    private void restoreOnTransactionRollback(List<Move> completed) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        List<Move> snapshot = List.copyOf(completed);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    restore(snapshot);
                }
            }
        });
    }

    private void restore(List<Move> completed) {
        for (int index = completed.size() - 1; index >= 0; index--) {
            Move move = completed.get(index);
            try {
                Files.createDirectories(move.source().getParent());
                if (Files.exists(move.target()) && !Files.exists(move.source())) {
                    Files.move(move.target(), move.source());
                }
            } catch (IOException ignored) {
                // A failed compensation is surfaced by startup migration on the next boot.
            }
        }
    }

    private Video locked(Long id) {
        return videos.findByIdForUpdate(id)
                .orElseThrow(() -> new ModerationConflictException("Video not found"));
    }

    private void assertActiveOwner(Video video, String username) {
        LocalDateTime now = now();
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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

    private record Move(Path source, Path target) {
    }

    public static class PreviewNotFoundException extends RuntimeException {
        public PreviewNotFoundException(String message) {
            super(message);
        }
    }
}
