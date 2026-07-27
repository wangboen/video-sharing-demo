package com.videosharing.videosharingspringboot.dto;

public record CreateUploadSessionRequest(
        String title,
        String description,
        UploadFileRequest video,
        UploadFileRequest thumbnail
) {
}
