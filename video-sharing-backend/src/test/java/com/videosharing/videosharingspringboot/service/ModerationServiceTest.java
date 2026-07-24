package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {
    @Mock VideoRepository videos;
    @Mock UserRepository users;
    @TempDir Path temp;
    private ModerationService service;
    private User reviewer;
    private User other;
    private final Instant now = Instant.parse("2026-07-24T12:00:00Z");

    @BeforeEach
    void setUp() {
        reviewer = user(1L, "reviewer", Role.REVIEWER);
        other = user(2L, "other", Role.REVIEWER);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new ModerationService(videos, users, temp.resolve("pending-videos"),
                temp.resolve("pending-thumbnails"), temp.resolve("public-videos"),
                temp.resolve("public-thumbnails"), Duration.ofMinutes(15), clock);
    }

    @Test
    void claimReturnsExistingActiveLeaseForSameReviewer() {
        Video leased = pending(10L);
        leased.setLeaseOwner(reviewer);
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        when(users.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
        when(videos.findActiveLeaseForOwner(1L, VideoStatus.PENDING, now)).thenReturn(Optional.of(leased));

        assertEquals(10L, service.claimNext("reviewer").orElseThrow().id());
        verify(videos, never()).findClaimableForUpdate(any(), any(), any());
    }

    @Test
    void leaseDurationMustBeLongerThanTheFixedSixtySecondHeartbeat() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ModerationService(videos, users, temp.resolve("pending-videos"),
                        temp.resolve("pending-thumbnails"), temp.resolve("public-videos"),
                        temp.resolve("public-thumbnails"), Duration.ofSeconds(60),
                        Clock.fixed(now, ZoneOffset.UTC)));

        assertTrue(failure.getMessage().contains("greater than the 60-second staff heartbeat"));
    }

    @Test
    void claimUsesOldestAvailablePendingVideo() {
        Video oldest = pending(10L);
        when(users.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
        when(videos.findActiveLeaseForOwner(1L, VideoStatus.PENDING, now)).thenReturn(Optional.empty());
        when(videos.findClaimableForUpdate(VideoStatus.PENDING, now, PageRequest.of(0, 1)))
                .thenReturn(List.of(oldest));
        when(videos.save(oldest)).thenReturn(oldest);

        var claimed = service.claimNext("reviewer").orElseThrow();

        assertEquals(10L, claimed.id());
        assertEquals(reviewer, oldest.getLeaseOwner());
        assertEquals(now.plusSeconds(15 * 60), oldest.getLeaseExpiresAt());
    }

    @Test
    void heartbeatRejectsNonOwner() {
        Video leased = pending(10L);
        leased.setLeaseOwner(other);
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));

        assertThrows(ModerationConflictException.class, () -> service.heartbeat(10L, "reviewer"));
    }

    @Test
    void heartbeatExtendsOwnersUnexpiredLease() {
        Video leased = activeLease();
        leased.setLeaseExpiresAt(now.plusSeconds(1));
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        var response = service.heartbeat(10L, "reviewer");

        assertEquals(now.plusSeconds(15 * 60), response.leaseExpiresAt());
    }

    @Test
    void ownReleaseClearsLease() {
        Video leased = pending(10L);
        leased.setLeaseOwner(reviewer);
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        leased.setClaimedAt(now.minusSeconds(60));
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        service.release(10L, "reviewer", false, Role.REVIEWER);

        assertNull(leased.getLeaseOwner());
        assertNull(leased.getLeaseExpiresAt());
    }

    @Test
    void forceReleaseRequiresAdmin() {
        Video leased = pending(10L);
        leased.setLeaseOwner(other);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.release(10L, "reviewer", true, Role.REVIEWER));
    }

    @Test
    void adminMayForceReleaseAnotherReviewersLease() {
        Video leased = pending(10L);
        leased.setLeaseOwner(other);
        leased.setClaimedAt(now.minusSeconds(60));
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        service.release(10L, "admin", true, Role.ADMIN);

        assertNull(leased.getLeaseOwner());
        assertNull(leased.getClaimedAt());
        assertNull(leased.getLeaseExpiresAt());
    }

    @Test
    void nonOwnerCannotDownloadPreview() {
        Video leased = activeLease();
        when(videos.findById(10L)).thenReturn(Optional.of(leased));

        assertThrows(ModerationConflictException.class,
                () -> service.preview(10L, "other", false));
    }

    @Test
    void expiredLeaseCanBeReassignedAndOldOwnerCannotDecide() {
        Video leased = pending(10L);
        leased.setLeaseOwner(other);
        leased.setLeaseExpiresAt(now.minusSeconds(1));
        when(users.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
        when(videos.findActiveLeaseForOwner(1L, VideoStatus.PENDING, now)).thenReturn(Optional.empty());
        when(videos.findClaimableForUpdate(VideoStatus.PENDING, now, PageRequest.of(0, 1)))
                .thenReturn(List.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        service.claimNext("reviewer");

        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "other", decision("REJECT", "no", leased.getVersion())));
    }

    @Test
    void optimisticVersionConflictPreventsDecision() {
        Video leased = activeLease();
        leased.setVersion(4L);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "reviewer", decision("APPROVE", null, 3L)));
    }

    @Test
    void rejectionRequiresTrimmedReasonAndClearsLease() {
        Video leased = activeLease();
        leased.setVersion(4L);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        var result = service.decide(10L, "reviewer", decision("REJECT", "  duplicate  ", 4L));

        assertEquals(VideoStatus.REJECTED, result.status());
        assertEquals("duplicate", leased.getRejectionReason());
        assertNull(leased.getLeaseOwner());
        assertEquals(now, leased.getReviewedAt());
    }

    @Test
    void rejectionRejectsBlankReason() {
        Video leased = activeLease();
        leased.setVersion(4L);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        assertThrows(IllegalArgumentException.class,
                () -> service.decide(10L, "reviewer", decision("REJECT", "   ", 4L)));
    }

    @Test
    void rejectionRejectsReasonLongerThanFiveHundredCharacters() {
        Video leased = activeLease();
        leased.setVersion(4L);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        assertThrows(IllegalArgumentException.class,
                () -> service.decide(10L, "reviewer", decision("REJECT", "x".repeat(501), 4L)));
    }

    @Test
    void approvalMovesMediaPairAndClearsLease() throws Exception {
        Video leased = activeLease();
        leased.setVersion(4L);
        leased.setFilePath("video.mp4");
        leased.setThumbnailPath("thumb.png");
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.createDirectories(temp.resolve("pending-thumbnails"));
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");
        Files.writeString(temp.resolve("pending-thumbnails/thumb.png"), "thumb");
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));

        service.decide(10L, "reviewer", decision("APPROVE", "ignored", 4L));

        assertEquals(VideoStatus.APPROVED, leased.getStatus());
        assertNull(leased.getRejectionReason());
        assertTrue(Files.exists(temp.resolve("public-videos/video.mp4")));
        assertTrue(Files.exists(temp.resolve("public-thumbnails/thumb.png")));
        assertNull(leased.getLeaseOwner());
    }

    @Test
    void approvalFailsLoudlyAndPreservesOriginalCauseWhenCompensationCannotReverseMove() throws Exception {
        Video leased = activeLease();
        leased.setVersion(4L);
        leased.setFilePath("video.mp4");
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        RuntimeException databaseFailure = new RuntimeException("database commit failed");
        when(videos.saveAndFlush(leased)).thenAnswer(invocation -> {
            Files.writeString(temp.resolve("pending-videos/video.mp4"), "collision");
            throw databaseFailure;
        });

        ModerationConflictException failure = assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "reviewer", decision("APPROVE", null, 4L)));

        assertTrue(failure.getMessage().contains("compensation failed"));
        assertSame(databaseFailure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("Unsafe compensation state"));
        assertTrue(Files.exists(temp.resolve("public-videos/video.mp4")));
        assertTrue(Files.exists(temp.resolve("pending-videos/video.mp4")));
    }

    @Test
    void duplicateDecisionIsConflict() {
        Video decided = pending(10L);
        decided.setStatus(VideoStatus.APPROVED);
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(decided));
        assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "reviewer", decision("APPROVE", null, 0L)));
    }

    private Video activeLease() {
        Video video = pending(10L);
        video.setLeaseOwner(reviewer);
        video.setLeaseExpiresAt(now.plusSeconds(60));
        return video;
    }

    private Video pending(long id) {
        Video video = new Video();
        video.setId(id);
        video.setStatus(VideoStatus.PENDING);
        video.setVersion(0L);
        video.setUploadDate(LocalDateTime.of(2026, 7, 24, 11, 0));
        video.setUser(user(9L, "creator", Role.USER));
        video.setTitle("Title");
        return video;
    }

    private User user(long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private ModerationDecisionRequest decision(String decision, String reason, long version) {
        return new ModerationDecisionRequest(decision, reason, version);
    }
}
