package com.videosharing.videosharingspringboot.dto;

public record CategoryUpsertRequest(
        String code,
        String name,
        String description,
        Integer sortOrder
) {
}
