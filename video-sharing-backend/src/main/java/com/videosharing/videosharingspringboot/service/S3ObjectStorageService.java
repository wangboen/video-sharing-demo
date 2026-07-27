package com.videosharing.videosharingspringboot.service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;

public class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorageService(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration expiration) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(objectRequest)
                .build();
        var presigned = presigner.presignPutObject(presignRequest);
        return new PresignedUpload(URI.create(presigned.url().toString()), Instant.now().plus(expiration));
    }

    @Override
    public PresignedUpload presignDownload(String key, String downloadFileName, Duration expiration) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + safeDownloadName(downloadFileName) + "\"")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(objectRequest)
                .build();
        var presigned = presigner.presignGetObject(presignRequest);
        return new PresignedUpload(URI.create(presigned.url().toString()), Instant.now().plus(expiration));
    }

    @Override
    public StoredObject get(String key) {
        ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        GetObjectResponse metadata = response.response();
        return new StoredObject(key, metadata.contentType(), metadata.contentLength(), response);
    }

    @Override
    public void copy(String sourceKey, String targetKey, String contentType) {
        client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(targetKey)
                .contentType(contentType)
                .metadataDirective("REPLACE")
                .build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private static String safeDownloadName(String downloadFileName) {
        String normalized = downloadFileName == null ? "download" : downloadFileName.replace('\\', '_').replace('/', '_');
        return normalized.replace("\"", "");
    }

}
