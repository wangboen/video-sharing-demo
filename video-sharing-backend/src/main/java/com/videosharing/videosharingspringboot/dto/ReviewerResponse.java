package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;

import java.time.LocalDateTime;

public record ReviewerResponse(
        Long id,
        String username,
        String email,
        Role role,
        LocalDateTime createdAt
) {
    public static ReviewerResponse from(StaffAccount staff) {
        return new ReviewerResponse(staff.getId(), staff.getUsername(), staff.getEmail(),
                staff.getRole(), staff.getCreatedAt());
    }
}
