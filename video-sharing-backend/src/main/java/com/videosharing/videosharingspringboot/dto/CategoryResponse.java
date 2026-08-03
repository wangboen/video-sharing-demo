package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.VideoCategory;

public record CategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        int sortOrder,
        boolean enabled
) {
    public static CategoryResponse from(VideoCategory category) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                category.isEnabled());
    }
}
