package com.videosharing.videosharingspringboot.messaging;

public class CommentPublishException extends RuntimeException {
    public CommentPublishException(String message) {
        super(message);
    }

    public CommentPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
