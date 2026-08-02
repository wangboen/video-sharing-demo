package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.RegisterRequest;
import com.videosharing.videosharingspringboot.dto.ReviewerResponse;
import com.videosharing.videosharingspringboot.dto.ReviewerReviewRecordResponse;
import com.videosharing.videosharingspringboot.service.ReviewerSupervisionService;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviewers")
public class AdminReviewerController {
    private final StaffAccountService staffAccounts;
    private final ReviewerSupervisionService supervision;

    public AdminReviewerController(StaffAccountService staffAccounts, ReviewerSupervisionService supervision) {
        this.staffAccounts = staffAccounts;
        this.supervision = supervision;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewerResponse.from(
                staffAccounts.createReviewer(request.getUsername(), request.getEmail(), request.getPassword())));
    }

    @GetMapping
    public Page<ReviewerResponse> list(@RequestParam(defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return supervision.listReviewers(q, PageRequest.of(normalizePage(page), normalizeSize(size)));
    }

    @GetMapping("/{id}/reviews")
    public Page<ReviewerReviewRecordResponse> reviews(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "") String q,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return supervision.listReviews(id, q, PageRequest.of(normalizePage(page), normalizeSize(size)));
    }

    private static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
