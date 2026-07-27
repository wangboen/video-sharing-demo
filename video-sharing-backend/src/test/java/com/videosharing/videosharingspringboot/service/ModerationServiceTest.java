package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {
    @Mock VideoRepository videos;
    @Mock StaffAccountRepository staffAccounts;
    @TempDir Path temp;
    private ModerationService service;
    private FakeObjectStorage objectStorage;
    private StaffAccount reviewer;
    private StaffAccount other;
    private final Instant now = Instant.parse("2026-07-24T12:00:00Z");

    @BeforeEach
    void setUp() {
        reviewer = staff(1L, "reviewer", Role.REVIEWER);
        other = staff(2L, "other", Role.REVIEWER);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        objectStorage = new FakeObjectStorage();
        service = new ModerationService(videos, staffAccounts, Duration.ofMinutes(15), clock, objectStorage,
                temp.resolve("validation"), "http://minio.local");
    }

    @Test
    void claimReturnsExistingActiveLeaseForSameReviewerWithPresignedPreviewUrls() {
        Video leased = pending(10L);
        leased.setFilePath("pending/users/9/session/video/a space.mp4");
        leased.setThumbnailPath("pending/users/9/session/thumbnail/cover image.png");
        leased.setLeaseOwner(reviewer);
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        when(staffAccounts.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
        when(videos.findActiveLeaseForOwner(1L, VideoStatus.PENDING, now)).thenReturn(Optional.of(leased));

        var response = service.claimNext("reviewer").orElseThrow();

        assertEquals(10L, response.id());
        assertEquals("http://minio.local/download/pending/users/9/session/video/a%20space.mp4", response.videoUrl());
        assertEquals("http://minio.local/download/pending/users/9/session/thumbnail/cover%20image.png",
                response.thumbnailUrl());
        verify(videos, never()).findClaimableForUpdate(any(), any(), any());
    }

    @Test
    void heartbeatReturnsPresignedMediaUrlsAndNullThumbnailUrlWhenMissing() {
        Video leased = activeLease();
        leased.setFilePath("pending/users/9/session/video/video.mp4");
        leased.setThumbnailPath(null);
        leased.setLeaseExpiresAt(now.plusSeconds(1));
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        var response = service.heartbeat(10L, "reviewer");

        assertEquals("http://minio.local/download/pending/users/9/session/video/video.mp4", response.videoUrl());
        assertNull(response.thumbnailUrl());
        assertFalse(response.hasThumbnail());
    }

    @Test
    void leaseDurationMustBeLongerThanTheFixedSixtySecondHeartbeat() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ModerationService(videos, staffAccounts, Duration.ofSeconds(60),
                        Clock.fixed(now, ZoneOffset.UTC)));

        assertTrue(failure.getMessage().contains("greater than the 60-second staff heartbeat"));
    }

    @Test
    void claimUsesOldestAvailablePendingVideo() {
        Video oldest = pending(10L);
        when(staffAccounts.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
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
    void expiredLeaseCanBeReassignedAndOldOwnerCannotDecide() {
        Video leased = pending(10L);
        leased.setLeaseOwner(other);
        leased.setLeaseExpiresAt(now.minusSeconds(1));
        when(staffAccounts.findByUsernameForUpdate("reviewer")).thenReturn(Optional.of(reviewer));
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
        leased.setFilePath("pending/users/9/session/video/video.mp4");
        leased.setThumbnailPath("pending/users/9/session/thumbnail/thumb.png");
        objectStorage.put(leased.getFilePath(), "video/mp4", MediaFixtures.mp4());
        objectStorage.put(leased.getThumbnailPath(), "image/png", MediaFixtures.png());
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.save(leased)).thenReturn(leased);

        var result = service.decide(10L, "reviewer", decision("REJECT", "  duplicate  ", 4L));

        assertEquals(VideoStatus.REJECTED, result.status());
        assertEquals("duplicate", leased.getRejectionReason());
        assertNull(leased.getLeaseOwner());
        assertEquals(now, leased.getReviewedAt());
        assertEquals("pending/users/9/session/video/video.mp4", leased.getFilePath());
        assertEquals("pending/users/9/session/thumbnail/thumb.png", leased.getThumbnailPath());
        assertTrue(objectStorage.contains(leased.getFilePath()));
        assertTrue(objectStorage.contains(leased.getThumbnailPath()));
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

    @Test
    void approvalPublishesPendingObjectStorageMediaAndDeletesPrivateObjects() {
        Video leased = activeLease();
        leased.setVersion(4L);
        leased.setFilePath("pending/users/9/session/video/video.mp4");
        leased.setThumbnailPath("pending/users/9/session/thumbnail/thumb.png");
        objectStorage.put(leased.getFilePath(), "video/mp4", MediaFixtures.mp4());
        objectStorage.put(leased.getThumbnailPath(), "image/png", MediaFixtures.png());
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));

        service.decide(10L, "reviewer", decision("APPROVE", null, 4L));

        assertEquals(VideoStatus.APPROVED, leased.getStatus());
        assertTrue(leased.getFilePath().startsWith("public/"));
        assertTrue(leased.getFilePath().endsWith(".mp4"));
        assertTrue(leased.getThumbnailPath().startsWith("public/"));
        assertTrue(objectStorage.contains(leased.getFilePath()));
        assertTrue(objectStorage.contains(leased.getThumbnailPath()));
        assertFalse(objectStorage.contains("pending/users/9/session/video/video.mp4"));
        assertFalse(objectStorage.contains("pending/users/9/session/thumbnail/thumb.png"));
    }

    @Test
    void approvalUsesConfiguredPublicPrefixAndDeletesOriginalsAfterDatabaseSave() {
        service = new ModerationService(videos, staffAccounts, Duration.ofMinutes(15),
                Clock.fixed(now, ZoneOffset.UTC), objectStorage, temp.resolve("validation"),
                "http://minio.local", "incoming/media/", "published/media/");
        Video leased = activeLease();
        leased.setVersion(4L);
        leased.setFilePath("incoming/media/users/9/session/video/video.mp4");
        leased.setThumbnailPath(null);
        objectStorage.put(leased.getFilePath(), "video/mp4", MediaFixtures.mp4());
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.saveAndFlush(leased)).thenAnswer(invocation -> {
            assertTrue(objectStorage.contains("incoming/media/users/9/session/video/video.mp4"));
            return leased;
        });

        service.decide(10L, "reviewer", decision("APPROVE", null, 4L));

        assertTrue(leased.getFilePath().startsWith("published/media/"));
        assertTrue(objectStorage.contains(leased.getFilePath()));
        assertFalse(objectStorage.contains("incoming/media/users/9/session/video/video.mp4"));
    }

    @Test
    void approvalKeepsPendingObjectsWhenDatabaseSaveFailsAfterPublishingCopy() {
        Video leased = activeLease();
        leased.setVersion(4L);
        leased.setFilePath("pending/users/9/session/video/video.mp4");
        leased.setThumbnailPath("pending/users/9/session/thumbnail/thumb.png");
        objectStorage.put(leased.getFilePath(), "video/mp4", MediaFixtures.mp4());
        objectStorage.put(leased.getThumbnailPath(), "image/png", MediaFixtures.png());
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        when(videos.saveAndFlush(leased)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "reviewer", decision("APPROVE", null, 4L)));

        assertTrue(objectStorage.contains("pending/users/9/session/video/video.mp4"));
        assertTrue(objectStorage.contains("pending/users/9/session/thumbnail/thumb.png"));
    }

    private StaffAccount staff(long id, String username, Role role) {
        StaffAccount staff = new StaffAccount();
        staff.setId(id);
        staff.setUsername(username);
        staff.setRole(role);
        return staff;
    }

    private ModerationDecisionRequest decision(String decision, String reason, long version) {
        return new ModerationDecisionRequest(decision, reason, version);
    }

    private static final class FakeObjectStorage implements ObjectStorageService {
        private final Map<String, Stored> objects = new HashMap<>();

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/" + key), Instant.now().plus(expiration));
        }

        @Override
        public PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration) {
            return new PresignedUpload(URI.create("http://minio.local/download/" + key.replace(" ", "%20")),
                    Instant.now().plus(expiration));
        }

        @Override
        public StoredObject get(String key) {
            Stored stored = objects.get(key);
            if (stored == null) {
                throw new IllegalArgumentException("Missing object " + key);
            }
            return new StoredObject(key, stored.contentType(), stored.content().length,
                    new ByteArrayInputStream(stored.content()));
        }

        @Override
        public void copy(String sourceKey, String targetKey, String contentType) {
            Stored stored = objects.get(sourceKey);
            objects.put(targetKey, new Stored(contentType, stored.content()));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        void put(String key, String contentType, byte[] content) {
            objects.put(key, new Stored(contentType, content));
        }

        boolean contains(String key) {
            return objects.containsKey(key);
        }

        private record Stored(String contentType, byte[] content) {
        }
    }
}
