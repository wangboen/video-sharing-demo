package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModerationConcurrencyTest {
    @Autowired VideoRepository videos;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @TempDir Path temp;
    private ModerationService moderation;

    @BeforeEach
    void setUp() {
        videos.deleteAll();
        users.deleteAll();
        moderation = new ModerationService(videos, users,
                temp.resolve("pending-videos"), temp.resolve("pending-thumbnails"),
                temp.resolve("public-videos"), temp.resolve("public-thumbnails"),
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-07-24T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void twoReviewersClaimingConcurrentlyReceiveDifferentVideos() throws Exception {
        User creator = saveUser("creator", Role.USER);
        saveUser("reviewer-1", Role.REVIEWER);
        saveUser("reviewer-2", Role.REVIEWER);
        saveVideo(creator, "first");
        saveVideo(creator, "second");

        var results = concurrentClaims();

        assertTrue(results.first().isPresent());
        assertTrue(results.second().isPresent());
        assertNotEquals(results.first().orElseThrow().id(), results.second().orElseThrow().id());
    }

    @Test
    void onlyOneReviewerSucceedsWhenOneVideoRemains() throws Exception {
        User creator = saveUser("creator", Role.USER);
        saveUser("reviewer-1", Role.REVIEWER);
        saveUser("reviewer-2", Role.REVIEWER);
        saveVideo(creator, "only");

        var results = concurrentClaims();

        assertEquals(1, (results.first().isPresent() ? 1 : 0) + (results.second().isPresent() ? 1 : 0));
    }

    private ClaimResults concurrentClaims() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ModerationVideoResponse>> first =
                    executor.submit(() -> claim("reviewer-1", ready, start));
            Future<Optional<ModerationVideoResponse>> second =
                    executor.submit(() -> claim("reviewer-2", ready, start));
            ready.await();
            start.countDown();
            return new ClaimResults(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<ModerationVideoResponse> claim(String username,
                                                    CountDownLatch ready,
                                                    CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return transactions.execute(status -> moderation.claimNext(username));
    }

    private User saveUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("encoded-password");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private void saveVideo(User creator, String title) {
        Video video = new Video();
        video.setUser(creator);
        video.setTitle(title);
        video.setFilePath(title + ".mp4");
        video.setStatus(VideoStatus.PENDING);
        videos.saveAndFlush(video);
    }

    private record ClaimResults(Optional<ModerationVideoResponse> first,
                                Optional<ModerationVideoResponse> second) {
    }
}
