package com.videosharing.videosharingspringboot.dto;

public record TagUpsertRequest(
        String code,
        String name,
        String description,
        Long categoryId,
        Integer sortOrder
) {
}
