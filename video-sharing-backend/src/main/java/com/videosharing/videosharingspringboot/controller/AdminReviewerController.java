package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.RegisterRequest;
import com.videosharing.videosharingspringboot.dto.ReviewerResponse;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviewers")
public class AdminReviewerController {
    private final StaffAccountService staffAccounts;

    public AdminReviewerController(StaffAccountService staffAccounts) {
        this.staffAccounts = staffAccounts;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ReviewerResponse.from(
                    staffAccounts.createReviewer(request.getUsername(), request.getEmail(), request.getPassword())));
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @GetMapping
    public List<ReviewerResponse> list() {
        return staffAccounts.listReviewers().stream().map(ReviewerResponse::from).toList();
    }
}
