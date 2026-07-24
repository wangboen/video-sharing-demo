package com.videosharing.videosharingspringboot.service;

public class ModerationConflictException extends RuntimeException {
    public ModerationConflictException(String message) {
        super(message);
    }

    public ModerationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
