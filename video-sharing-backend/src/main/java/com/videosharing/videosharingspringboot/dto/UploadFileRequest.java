package com.videosharing.videosharingspringboot.dto;

public record UploadFileRequest(
        String fileName,
        String contentType,
        long size
) {
}
