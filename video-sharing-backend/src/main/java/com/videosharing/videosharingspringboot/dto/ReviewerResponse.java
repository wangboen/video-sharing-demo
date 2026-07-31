package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;

import java.time.Instant;
import java.time.LocalDateTime;

public record ReviewerResponse(
        Long id,
        String username,
        String email,
        Role role,
        LocalDateTime createdAt,
        long reviewedTotal,
        long approvedTotal,
        long rejectedTotal,
        Instant lastReviewedAt
) {
    public static ReviewerResponse from(StaffAccount staff) {
        return new ReviewerResponse(staff.getId(), staff.getUsername(), staff.getEmail(),
                staff.getRole(), staff.getCreatedAt(), 0, 0, 0, null);
    }

    public static ReviewerResponse from(StaffAccount staff,
                                        long reviewedTotal,
                                        long approvedTotal,
                                        long rejectedTotal,
                                        Instant lastReviewedAt) {
        return new ReviewerResponse(staff.getId(), staff.getUsername(), staff.getEmail(),
                staff.getRole(), staff.getCreatedAt(), reviewedTotal, approvedTotal, rejectedTotal, lastReviewedAt);
    }
}
