package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.service.ObjectStorageService;
import com.videosharing.videosharingspringboot.service.S3ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class ObjectStorageConfig {
    @Bean
    ObjectStorageService objectStorageService(
            @Value("${object-storage.endpoint}") String endpoint,
            @Value("${object-storage.region}") String region,
            @Value("${object-storage.bucket}") String bucket,
            @Value("${object-storage.access-key}") String accessKey,
            @Value("${object-storage.secret-key}") String secretKey) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        URI endpointUri = URI.create(endpoint);
        S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(region))
                .credentialsProvider(provider)
                .serviceConfiguration(serviceConfiguration)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(region))
                .credentialsProvider(provider)
                .serviceConfiguration(serviceConfiguration)
                .build();
        return new S3ObjectStorageService(client, presigner, bucket);
    }
}
