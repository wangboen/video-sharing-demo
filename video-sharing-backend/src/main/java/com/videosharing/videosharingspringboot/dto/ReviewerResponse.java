package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.User;

import java.time.LocalDateTime;

public record ReviewerResponse(
        Long id,
        String username,
        String email,
        Role role,
        LocalDateTime createdAt
) {
    public static ReviewerResponse from(User user) {
        return new ReviewerResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getCreatedAt());
    }
}
