package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.VideoTag;

public record TagResponse(
        Long id,
        String code,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        int sortOrder,
        boolean enabled
) {
    public static TagResponse from(VideoTag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getCategory() == null ? null : tag.getCategory().getId(),
                tag.getCategory() == null ? null : tag.getCategory().getName(),
                tag.getSortOrder(),
                tag.isEnabled());
    }
}
