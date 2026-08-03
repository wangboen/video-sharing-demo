package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ReviewerReviewRecordResponse;
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
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ReviewerSupervisionServiceTest {
    @Autowired StaffAccountRepository staffAccounts;
    @Autowired UserRepository users;
    @Autowired VideoRepository videos;
    @Autowired VideoCategoryRepository categories;
    private ReviewerSupervisionService service;
    private User creator;
    private StaffAccount reviewerOne;
    private StaffAccount reviewerTwo;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @BeforeEach
    void setUp() {
        service = new ReviewerSupervisionService(staffAccounts, videos);
        String suffix = String.valueOf(COUNTER.incrementAndGet());
        creator = saveUser("sup-creator-" + suffix);
        reviewerOne = saveStaff("sup-reviewer-one-" + suffix, "one-" + suffix + "@example.com", Role.REVIEWER);
        reviewerTwo = saveStaff("sup-reviewer-two-" + suffix, "two-" + suffix + "@example.com", Role.REVIEWER);
        saveStaff("sup-admin-" + suffix, "admin-" + suffix + "@example.com", Role.ADMIN);
    }

    @Test
    void reviewerListSearchesAndPaginatesReviewersWithCurrentPageStatistics() {
        reviewed(reviewerOne, "approved old", VideoStatus.APPROVED, Instant.parse("2026-07-24T08:00:00Z"), null);
        reviewed(reviewerOne, "approved latest", VideoStatus.APPROVED, Instant.parse("2026-07-25T08:00:00Z"), null);
        reviewed(reviewerOne, "rejected", VideoStatus.REJECTED, Instant.parse("2026-07-25T07:00:00Z"), "duplicate");
        reviewed(reviewerOne, "pending ignored", VideoStatus.PENDING, null, null);
        reviewed(reviewerTwo, "other reviewer", VideoStatus.REJECTED, Instant.parse("2026-07-26T08:00:00Z"), "policy");

        var page = service.listReviewers("one-", PageRequest.of(0, 1));

        assertEquals(1, page.getTotalElements());
        var row = page.getContent().get(0);
        assertEquals(reviewerOne.getId(), row.id());
        assertEquals(3, row.reviewedTotal());
        assertEquals(2, row.approvedTotal());
        assertEquals(1, row.rejectedTotal());
        assertEquals(Instant.parse("2026-07-25T08:00:00Z"), row.lastReviewedAt());
    }

    @Test
    void reviewerReviewsSearchAndPaginateOnlyReviewedVideosNewestFirst() {
        reviewed(reviewerOne, "City timelapse", VideoStatus.APPROVED, Instant.parse("2026-07-24T08:00:00Z"), null);
        reviewed(reviewerOne, "Kitchen clip", VideoStatus.REJECTED, Instant.parse("2026-07-25T08:00:00Z"), "unsafe food");
        reviewed(reviewerOne, "Travel note", VideoStatus.APPROVED, Instant.parse("2026-07-26T08:00:00Z"), null);
        reviewed(reviewerTwo, "Kitchen clip by other reviewer", VideoStatus.REJECTED,
                Instant.parse("2026-07-27T08:00:00Z"), "unsafe food");
        reviewed(reviewerOne, "Still pending", VideoStatus.PENDING, null, null);

        var page = service.listReviews(reviewerOne.getId(), "kit", PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        ReviewerReviewRecordResponse record = page.getContent().get(0);
        assertEquals("Kitchen clip", record.title());
        assertEquals(creator.getUsername(), record.uploader());
        assertEquals(VideoStatus.REJECTED, record.status());
        assertEquals("unsafe food", record.rejectionReason());
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("encoded-password");
        user.setRole(Role.USER);
        return users.saveAndFlush(user);
    }

    private StaffAccount saveStaff(String username, String email, Role role) {
        StaffAccount staff = new StaffAccount();
        staff.setUsername(username);
        staff.setEmail(email);
        staff.setPassword("encoded-password");
        staff.setRole(role);
        return staffAccounts.saveAndFlush(staff);
    }

    private Video reviewed(StaffAccount reviewer,
                           String title,
                           VideoStatus status,
                           Instant reviewedAt,
                           String rejectionReason) {
        Video video = new Video();
        video.setUser(creator);
        video.setCategory(saveCategory());
        video.setTitle(title);
        video.setDescription("description");
        video.setFilePath(title + ".mp4");
        video.setStatus(status);
        video.setReviewer(reviewer);
        video.setReviewedAt(reviewedAt);
        video.setRejectionReason(rejectionReason);
        return videos.saveAndFlush(video);
    }

    private VideoCategory saveCategory() {
        VideoCategory category = new VideoCategory();
        category.setCode("supervision-category-" + COUNTER.incrementAndGet());
        category.setName("电影");
        return categories.saveAndFlush(category);
    }
}
