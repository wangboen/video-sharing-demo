package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VideoServiceSearchTest {
    private final VideoRepository repository = mock(VideoRepository.class);
    private final VideoService service = new VideoService(repository, null, "build/validation",
            Duration.ofMinutes(15));

    @Test
    void nonBlankPublicSearchUsesRepositorySearch() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(repository.searchPublicVideos(VideoStatus.APPROVED, "spring", pageable)).thenReturn(result);

        assertEquals(result, service.searchPublicVideos(" spring ", pageable));

        verify(repository).searchPublicVideos(VideoStatus.APPROVED, "spring", pageable);
    }

    @Test
    void blankPublicSearchStillUsesRecommendedVideos() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(repository.findAllByStatusOrderByViewsDescUploadDateDescIdDesc(VideoStatus.APPROVED, pageable))
                .thenReturn(result);

        assertEquals(result, service.searchPublicVideos("   ", pageable));
    }

    @Test
    void recommendedVideosByTagUsesApprovedTagRelationQuery() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(repository.findRecommendedByTag(VideoStatus.APPROVED, 42L, pageable)).thenReturn(result);

        assertEquals(result, service.getRecommendedVideosByTag(42L, pageable));

        verify(repository).findRecommendedByTag(VideoStatus.APPROVED, 42L, pageable);
    }

    @Test
    void recommendedVideosByCategoryUsesApprovedCategoryQuery() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(repository.findRecommendedByCategory(VideoStatus.APPROVED, 7L, pageable)).thenReturn(result);

        assertEquals(result, service.getRecommendedVideosByCategory(7L, pageable));

        verify(repository).findRecommendedByCategory(VideoStatus.APPROVED, 7L, pageable);
    }

    private static Video video() {
        Video video = new Video();
        video.setId(10L);
        video.setStatus(VideoStatus.APPROVED);
        video.setTitle("Spring");
        return video;
    }
}
