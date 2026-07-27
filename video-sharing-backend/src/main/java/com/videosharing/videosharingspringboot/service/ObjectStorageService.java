package com.videosharing.videosharingspringboot.service;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

public interface ObjectStorageService {
    PresignedUpload presignUpload(String key, String contentType, Duration expiration);

    PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration);

    StoredObject get(String key);

    void copy(String sourceKey, String targetKey, String contentType);

    void delete(String key);

    record PresignedUpload(URI url, Instant expiresAt) {
    }

    record StoredObject(String key, String contentType, long contentLength, InputStream inputStream) {
    }

    class ObjectNotFoundException extends RuntimeException {
        public ObjectNotFoundException(String key) {
            super("Object not found: " + key);
        }
    }
}
