package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.repository.VideoCategoryRepository;
import com.videosharing.videosharingspringboot.repository.VideoTagRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryTagInitializerTest {
    private final VideoCategoryRepository categories = mock(VideoCategoryRepository.class);
    private final VideoTagRepository tags = mock(VideoTagRepository.class);

    @Test
    void initializesFixedCategoriesAndDefaultTagsByCode() throws Exception {
        when(categories.findByCode("movie")).thenReturn(Optional.empty());
        when(categories.findByCode("tv")).thenReturn(Optional.empty());
        when(categories.findByCode("anime")).thenReturn(Optional.empty());
        when(categories.findByCode("game")).thenReturn(Optional.empty());
        when(categories.findByCode("tech")).thenReturn(Optional.empty());
        when(categories.findByCode("sports")).thenReturn(Optional.empty());
        when(categories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tags.existsByCode("movie_action")).thenReturn(false);
        when(tags.existsByCode("tv_cn")).thenReturn(false);
        when(tags.existsByCode("anime_hot_blood")).thenReturn(false);
        when(tags.existsByCode("game_live")).thenReturn(false);
        when(tags.existsByCode("tech_ai")).thenReturn(false);
        when(tags.existsByCode("sports_basketball")).thenReturn(false);

        new CategoryTagInitializer(categories, tags).run(null);

        verify(categories).save(argThat(category -> "movie".equals(category.getCode())
                && "电影".equals(category.getName()) && category.getSortOrder() == 10));
        verify(categories).save(argThat(category -> "tv".equals(category.getCode())));
        verify(categories).save(argThat(category -> "anime".equals(category.getCode())));
        verify(categories).save(argThat(category -> "game".equals(category.getCode())));
        verify(categories).save(argThat(category -> "tech".equals(category.getCode())));
        verify(categories).save(argThat(category -> "sports".equals(category.getCode()) && category.getSortOrder() == 60));
        verify(tags).save(argThat(tag -> "movie_action".equals(tag.getCode()) && "动作".equals(tag.getName())));
        verify(tags).save(argThat(tag -> "tv_cn".equals(tag.getCode()) && "国产剧".equals(tag.getName())));
        verify(tags).save(argThat(tag -> "anime_hot_blood".equals(tag.getCode()) && "热血".equals(tag.getName())));
        verify(tags).save(argThat(tag -> "game_live".equals(tag.getCode()) && "实况".equals(tag.getName())));
        verify(tags).save(argThat(tag -> "tech_ai".equals(tag.getCode()) && "AI".equals(tag.getName())));
        verify(tags).save(argThat(tag -> "sports_basketball".equals(tag.getCode()) && tag.getSortOrder() == 10));
    }
}
