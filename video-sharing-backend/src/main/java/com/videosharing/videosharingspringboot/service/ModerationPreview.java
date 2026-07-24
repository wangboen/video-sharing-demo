package com.videosharing.videosharingspringboot.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record ModerationPreview(Resource resource, MediaType mediaType, String safeFilename) {
}
