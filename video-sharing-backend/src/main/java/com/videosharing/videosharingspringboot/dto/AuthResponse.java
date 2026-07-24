package com.videosharing.videosharingspringboot.dto;

import lombok.Data;
import com.videosharing.videosharingspringboot.model.Role;

@Data
public class AuthResponse {
    private String message;
    private String token;
    private String tokenType;
    private String username;
    private Long id;
    private String email;
    private Role role;

    public AuthResponse(String message, String token, String username, Long id, String email, Role role) {
        this.message = message;
        this.token = token;
        this.tokenType = "Bearer";
        this.username = username;
        this.id = id;
        this.email = email;
        this.role = role;
    }
}
