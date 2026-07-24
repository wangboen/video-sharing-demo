package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModerationDecisionTransactionTest {
    @TempDir Path temp;

    @Test
    void commitFailureIsCaughtOutsideOwnedTransactionAndCompensatedSynchronously() throws Exception {
        VideoRepository videos = mock(VideoRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.parse("2026-07-24T03:00:00Z");
        User reviewer = new User();
        reviewer.setId(2L);
        reviewer.setUsername("reviewer");
        reviewer.setRole(Role.REVIEWER);
        Video leased = new Video();
        leased.setId(10L);
        leased.setStatus(VideoStatus.PENDING);
        leased.setVersion(4L);
        leased.setFilePath("legacy.mp4");
        leased.setLeaseOwner(reviewer);
        leased.setLeaseExpiresAt(now.plusSeconds(60));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.write(temp.resolve("pending-videos/legacy.mp4"), MediaFixtures.mp4());
        when(videos.findByIdForUpdate(10L)).thenReturn(Optional.of(leased));
        RuntimeException commitFailure = new RuntimeException("forced commit failure");
        when(videos.saveAndFlush(leased)).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw commitFailure;
                }
            });
            return leased;
        });
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:moderation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var service = new ModerationService(videos, users,
                temp.resolve("pending-videos"), temp.resolve("pending-thumbnails"),
                temp.resolve("public-videos"), temp.resolve("public-thumbnails"),
                Duration.ofMinutes(15), Clock.fixed(now, ZoneOffset.UTC), transaction);

        ModerationConflictException failure = assertThrows(ModerationConflictException.class,
                () -> service.decide(10L, "reviewer",
                        new ModerationDecisionRequest("APPROVE", null, 4L)));

        assertSame(commitFailure, failure.getCause());
        assertTrue(failure.getMessage().contains("forced commit failure"));
        assertEquals(VideoStatus.PENDING, leased.getStatus());
        assertEquals("legacy.mp4", leased.getFilePath());
        assertTrue(Files.isRegularFile(temp.resolve("pending-videos/legacy.mp4")));
        try (var published = Files.list(temp.resolve("public-videos"))) {
            assertEquals(0, published.count());
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
