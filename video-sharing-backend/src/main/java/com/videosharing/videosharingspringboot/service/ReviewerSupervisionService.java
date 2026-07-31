package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.ReviewerResponse;
import com.videosharing.videosharingspringboot.dto.ReviewerReviewRecordResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.ReviewerStatsProjection;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewerSupervisionService {
    private static final Collection<VideoStatus> REVIEWED_STATUSES =
            java.util.List.of(VideoStatus.APPROVED, VideoStatus.REJECTED);

    private final StaffAccountRepository staffAccounts;
    private final VideoRepository videos;

    public ReviewerSupervisionService(StaffAccountRepository staffAccounts, VideoRepository videos) {
        this.staffAccounts = staffAccounts;
        this.videos = videos;
    }

    @Transactional(readOnly = true)
    public Page<ReviewerResponse> listReviewers(String query, Pageable pageable) {
        Page<com.videosharing.videosharingspringboot.model.StaffAccount> reviewerPage =
                staffAccounts.searchByRole(Role.REVIEWER, normalize(query), pageable);
        var reviewerIds = reviewerPage.stream().map(staff -> staff.getId()).toList();
        Map<Long, ReviewerStatsProjection> statsByReviewer = reviewerIds.isEmpty()
                ? Map.of()
                : videos.summarizeReviewedByReviewerIds(reviewerIds, REVIEWED_STATUSES,
                                VideoStatus.APPROVED, VideoStatus.REJECTED).stream()
                        .collect(Collectors.toMap(ReviewerStatsProjection::getReviewerId, Function.identity()));

        return reviewerPage.map(staff -> {
            ReviewerStatsProjection stats = statsByReviewer.get(staff.getId());
            return ReviewerResponse.from(staff,
                    stats == null ? 0 : stats.getReviewedTotal(),
                    stats == null ? 0 : stats.getApprovedTotal(),
                    stats == null ? 0 : stats.getRejectedTotal(),
                    stats == null ? null : stats.getLastReviewedAt());
        });
    }

    @Transactional(readOnly = true)
    public Page<ReviewerReviewRecordResponse> listReviews(Long reviewerId, String query, Pageable pageable) {
        return videos.searchReviewedByReviewer(reviewerId, REVIEWED_STATUSES, normalize(query), pageable)
                .map(ReviewerReviewRecordResponse::from);
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }
}
