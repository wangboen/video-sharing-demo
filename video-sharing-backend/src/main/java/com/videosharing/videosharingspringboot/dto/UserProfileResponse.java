package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import com.videosharing.videosharingspringboot.model.Role;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private Role role;

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
