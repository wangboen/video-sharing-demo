package com.videosharing.videosharingspringboot.dto;

import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTagResponseTest {
    @Test
    void categoryResponseIncludesAdminEditableFields() {
        VideoCategory category = new VideoCategory();
        category.setId(1L);
        category.setCode("movie");
        category.setName("电影");
        category.setDescription("影视长片");
        category.setSortOrder(10);
        category.setEnabled(false);

        CategoryResponse response = CategoryResponse.from(category);

        assertEquals(1L, response.id());
        assertEquals("movie", response.code());
        assertEquals("电影", response.name());
        assertEquals("影视长片", response.description());
        assertEquals(10, response.sortOrder());
        assertFalse(response.enabled());
    }

    @Test
    void tagResponseIncludesCategoryAndAdminEditableFields() {
        VideoCategory category = new VideoCategory();
        category.setId(1L);
        category.setName("电影");

        VideoTag tag = new VideoTag();
        tag.setId(10L);
        tag.setCode("movie_action");
        tag.setName("动作");
        tag.setDescription("动作片");
        tag.setCategory(category);
        tag.setSortOrder(5);
        tag.setEnabled(true);

        TagResponse response = TagResponse.from(tag);

        assertEquals(10L, response.id());
        assertEquals("movie_action", response.code());
        assertEquals("动作", response.name());
        assertEquals("动作片", response.description());
        assertEquals(1L, response.categoryId());
        assertEquals("电影", response.categoryName());
        assertEquals(5, response.sortOrder());
        assertTrue(response.enabled());
    }
}
