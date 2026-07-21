package com.videosharing.videosharingspringboot.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String message;
    private String token;
    private String tokenType;
    private String username;
    private Long id;
    private String email;

    public AuthResponse(String message, String token, String username, Long id, String email) {
        this.message = message;
        this.token = token;
        this.tokenType = "Bearer";
        this.username = username;
        this.id = id;
        this.email = email;
    }
}
