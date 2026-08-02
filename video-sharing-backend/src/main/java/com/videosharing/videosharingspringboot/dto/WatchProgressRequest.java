package com.videosharing.videosharingspringboot.dto;

public record WatchProgressRequest(
        Integer positionSeconds,
        Integer durationSeconds,
        Boolean completed,
        String eventType
) {
}
