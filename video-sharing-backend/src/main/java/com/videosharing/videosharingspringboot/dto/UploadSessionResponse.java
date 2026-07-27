package com.videosharing.videosharingspringboot.dto;

import java.time.Instant;

public record UploadSessionResponse(
        String uploadSessionId,
        UploadTargetResponse video,
        UploadTargetResponse thumbnail,
        Instant expiresAt
) {
}
