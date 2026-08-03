package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import com.videosharing.videosharingspringboot.repository.VideoCategoryRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired VideoCategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired StaffAccountRepository staffAccounts;
    @Autowired TransactionTemplate transactions;
    private ModerationService moderation;

    @BeforeEach
    void setUp() {
        videos.deleteAll();
        staffAccounts.deleteAll();
        users.deleteAll();
        moderation = new ModerationService(videos, staffAccounts, Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-07-24T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void twoReviewersClaimingConcurrentlyReceiveDifferentVideos() throws Exception {
        User creator = saveUser("creator", Role.USER);
        saveStaff("reviewer-1", Role.REVIEWER);
        saveStaff("reviewer-2", Role.REVIEWER);
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
        saveStaff("reviewer-1", Role.REVIEWER);
        saveStaff("reviewer-2", Role.REVIEWER);
        saveVideo(creator, "only");

        var results = concurrentClaims();

        assertEquals(1, (results.first().isPresent() ? 1 : 0) + (results.second().isPresent() ? 1 : 0));
    }

    @Test
    void decisionSucceedsWithVersionReturnedByFreshClaim() {
        User creator = saveUser("creator", Role.USER);
        saveStaff("reviewer-1", Role.REVIEWER);
        saveVideo(creator, "pending");

        ModerationVideoResponse claim = transactions.execute(
                status -> moderation.claimNext("reviewer-1").orElseThrow());

        var decision = transactions.execute(status -> moderation.decide(
                claim.id(), "reviewer-1",
                new ModerationDecisionRequest("REJECT", "duplicate", claim.version())));

        assertEquals(VideoStatus.REJECTED, decision.status());
    }

    @Test
    void decisionSucceedsWithVersionReturnedByHeartbeat() {
        User creator = saveUser("creator", Role.USER);
        saveStaff("reviewer-1", Role.REVIEWER);
        saveVideo(creator, "pending");
        ModerationVideoResponse claim = transactions.execute(
                status -> moderation.claimNext("reviewer-1").orElseThrow());

        ModerationVideoResponse heartbeat = transactions.execute(
                status -> moderation.heartbeat(claim.id(), "reviewer-1"));

        var decision = transactions.execute(status -> moderation.decide(
                heartbeat.id(), "reviewer-1",
                new ModerationDecisionRequest("REJECT", "duplicate", heartbeat.version())));

        assertEquals(VideoStatus.REJECTED, decision.status());
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

    private StaffAccount saveStaff(String username, Role role) {
        StaffAccount staff = new StaffAccount();
        staff.setUsername(username);
        staff.setEmail(username + "@example.com");
        staff.setPassword("encoded-password");
        staff.setRole(role);
        return staffAccounts.saveAndFlush(staff);
    }

    private void saveVideo(User creator, String title) {
        Video video = new Video();
        video.setUser(creator);
        video.setCategory(saveCategory());
        video.setTitle(title);
        video.setFilePath(title + ".mp4");
        video.setStatus(VideoStatus.PENDING);
        videos.saveAndFlush(video);
    }

    private VideoCategory saveCategory() {
        VideoCategory category = new VideoCategory();
        category.setCode("moderation-category-" + java.util.UUID.randomUUID());
        category.setName("电影");
        return categories.saveAndFlush(category);
    }

    private record ClaimResults(Optional<ModerationVideoResponse> first,
                                Optional<ModerationVideoResponse> second) {
    }
}
