package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoTag;
import com.videosharing.videosharingspringboot.repository.VideoCategoryRepository;
import com.videosharing.videosharingspringboot.repository.VideoTagRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CategoryTagServiceTest {
    private final VideoCategoryRepository categories = mock(VideoCategoryRepository.class);
    private final VideoTagRepository tags = mock(VideoTagRepository.class);
    private final CategoryTagService service = new CategoryTagService(categories, tags);

    @Test
    void resolvesEnabledCategoryAndTagsBelongingToIt() {
        VideoCategory movie = category(1L, true);
        VideoTag action = tag(10L, movie, true);
        VideoTag review = tag(11L, movie, true);
        when(categories.findById(1L)).thenReturn(Optional.of(movie));
        when(tags.findAllById(any())).thenReturn(List.of(action, review));

        CategoryTagService.Selection selection = service.resolveUploadSelection(1L, List.of(10L, 10L, 11L));

        assertEquals(movie, selection.category());
        assertEquals(List.of(action, review), selection.tags());
    }

    @Test
    void rejectsMissingCategoryForUpload() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.resolveUploadSelection(null, List.of()));

        assertEquals("Video category is required", failure.getMessage());
    }

    @Test
    void rejectsTagOutsideSelectedCategory() {
        VideoCategory movie = category(1L, true);
        VideoCategory game = category(2L, true);
        when(categories.findById(1L)).thenReturn(Optional.of(movie));
        when(tags.findAllById(any())).thenReturn(List.of(tag(20L, game, true)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.resolveUploadSelection(1L, List.of(20L)));

        assertEquals("Video tags must belong to the selected category", failure.getMessage());
    }

    private static VideoCategory category(Long id, boolean enabled) {
        VideoCategory category = new VideoCategory();
        category.setId(id);
        category.setCode("category-" + id);
        category.setName("Category " + id);
        category.setEnabled(enabled);
        return category;
    }

    private static VideoTag tag(Long id, VideoCategory category, boolean enabled) {
        VideoTag tag = new VideoTag();
        tag.setId(id);
        tag.setCode("tag-" + id);
        tag.setName("Tag " + id);
        tag.setCategory(category);
        tag.setEnabled(enabled);
        return tag;
    }
}
