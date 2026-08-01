package com.videosharing.videosharingspringboot.service;

public class VideoSearchUnavailableException extends RuntimeException {
    public VideoSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public VideoSearchUnavailableException(String message) {
        super(message);
    }
}
