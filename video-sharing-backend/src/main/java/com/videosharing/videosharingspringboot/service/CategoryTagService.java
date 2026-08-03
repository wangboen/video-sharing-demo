package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.dto.CategoryUpsertRequest;
import com.videosharing.videosharingspringboot.dto.TagUpsertRequest;
import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoTag;
import com.videosharing.videosharingspringboot.repository.VideoCategoryRepository;
import com.videosharing.videosharingspringboot.repository.VideoTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class CategoryTagService {
    private static final int MAX_TAGS_PER_VIDEO = 10;

    private final VideoCategoryRepository categories;
    private final VideoTagRepository tags;

    public CategoryTagService(VideoCategoryRepository categories, VideoTagRepository tags) {
        this.categories = categories;
        this.tags = tags;
    }

    public List<VideoCategory> publicCategories() {
        return categories.findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    }

    public List<VideoTag> publicTags(Long categoryId) {
        return tags.findAllByCategoryIdAndEnabledTrueOrderBySortOrderAscIdAsc(categoryId);
    }

    public List<VideoCategory> adminCategories() {
        return categories.findAllByOrderBySortOrderAscIdAsc();
    }

    public List<VideoTag> adminTags(Long categoryId) {
        if (categoryId == null) {
            return tags.findAll();
        }
        return tags.findAllByCategoryIdOrderBySortOrderAscIdAsc(categoryId);
    }

    @Transactional
    public Selection resolveUploadSelection(Long categoryId, List<Long> tagIds) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Video category is required");
        }
        VideoCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Video category not found"));
        if (!category.isEnabled()) {
            throw new IllegalArgumentException("Video category is disabled");
        }
        List<Long> uniqueTagIds = new ArrayList<>(new LinkedHashSet<>(tagIds == null ? List.of() : tagIds));
        if (uniqueTagIds.size() > MAX_TAGS_PER_VIDEO) {
            throw new IllegalArgumentException("A video can have at most 10 tags");
        }
        if (uniqueTagIds.isEmpty()) {
            return new Selection(category, List.of());
        }
        List<VideoTag> resolvedTags = tags.findAllById(uniqueTagIds);
        if (resolvedTags.size() != uniqueTagIds.size()) {
            throw new IllegalArgumentException("Video tag not found");
        }
        for (VideoTag tag : resolvedTags) {
            if (!tag.isEnabled()) {
                throw new IllegalArgumentException("Video tag is disabled");
            }
            if (tag.getCategory() == null || !category.getId().equals(tag.getCategory().getId())) {
                throw new IllegalArgumentException("Video tags must belong to the selected category");
            }
        }
        return new Selection(category, resolvedTags);
    }

    @Transactional
    public VideoCategory saveCategory(Long id, CategoryUpsertRequest request) {
        VideoCategory category = id == null ? new VideoCategory()
                : categories.findById(id).orElseThrow(() -> new IllegalArgumentException("Video category not found"));
        category.setCode(required(request.code(), "Category code is required"));
        category.setName(required(request.name(), "Category name is required"));
        category.setDescription(blankToNull(request.description()));
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return categories.save(category);
    }

    @Transactional
    public VideoCategory setCategoryEnabled(Long id, boolean enabled) {
        VideoCategory category = categories.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video category not found"));
        category.setEnabled(enabled);
        return categories.save(category);
    }

    @Transactional
    public VideoTag saveTag(Long id, TagUpsertRequest request) {
        VideoCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Video category not found"));
        VideoTag tag = id == null ? new VideoTag()
                : tags.findById(id).orElseThrow(() -> new IllegalArgumentException("Video tag not found"));
        tag.setCode(required(request.code(), "Tag code is required"));
        tag.setName(required(request.name(), "Tag name is required"));
        tag.setDescription(blankToNull(request.description()));
        tag.setCategory(category);
        tag.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return tags.save(tag);
    }

    @Transactional
    public VideoTag setTagEnabled(Long id, boolean enabled) {
        VideoTag tag = tags.findById(id).orElseThrow(() -> new IllegalArgumentException("Video tag not found"));
        tag.setEnabled(enabled);
        return tags.save(tag);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Selection(VideoCategory category, List<VideoTag> tags) {
    }
}
