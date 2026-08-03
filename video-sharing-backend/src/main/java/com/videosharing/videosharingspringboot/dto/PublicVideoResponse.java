package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record PublicVideoResponse(
        Long id,
        String title,
        String description,
        String uploader,
        String videoUrl,
        String thumbnailUrl,
        CategoryResponse category,
        List<TagResponse> tags,
        LocalDateTime uploadDate,
        Long views
) {
    public static PublicVideoResponse from(Video video) {
        return from(video, "");
    }

    public static PublicVideoResponse from(Video video, String objectStoragePublicBaseUrl) {
        if (video.getStatus() != VideoStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved videos may have public media URLs");
        }
        return new PublicVideoResponse(video.getId(), video.getTitle(), video.getDescription(),
                video.getUser().getUsername(), publicVideoUrl(video.getFilePath(), objectStoragePublicBaseUrl),
                video.getThumbnailPath() == null ? null : publicThumbnailUrl(video.getThumbnailPath(), objectStoragePublicBaseUrl),
                CategoryResponse.from(video.getCategory()), tagResponses(video),
                video.getUploadDate(), video.getViews());
    }

    private static List<TagResponse> tagResponses(Video video) {
        if (video.getTags() == null || video.getTags().isEmpty()) {
            return List.of();
        }
        return video.getTags().stream()
                .sorted(Comparator.comparingInt(com.videosharing.videosharingspringboot.model.VideoTag::getSortOrder)
                        .thenComparing(com.videosharing.videosharingspringboot.model.VideoTag::getId,
                                Comparator.nullsLast(Long::compareTo)))
                .map(TagResponse::from)
                .toList();
    }

    private static String publicVideoUrl(String fileName, String objectStoragePublicBaseUrl) {
        return objectUrl(objectStoragePublicBaseUrl, fileName);
    }

    private static String publicThumbnailUrl(String fileName, String objectStoragePublicBaseUrl) {
        return objectUrl(objectStoragePublicBaseUrl, fileName);
    }

    private static String objectUrl(String baseUrl, String key) {
        String trimmed = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return trimmed + "/" + UriUtils.encodePath(key, StandardCharsets.UTF_8);
    }

}
