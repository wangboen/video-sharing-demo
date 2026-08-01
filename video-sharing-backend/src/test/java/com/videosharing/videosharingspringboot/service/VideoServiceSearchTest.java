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
    private final VideoSearchService search = mock(VideoSearchService.class);
    private final VideoService service = new VideoService(repository, null, "build/validation",
            Duration.ofMinutes(15), search);

    @Test
    void nonBlankPublicSearchUsesElasticsearch() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(search.searchApproved("spring", pageable)).thenReturn(result);

        assertEquals(result, service.searchPublicVideos(" spring ", pageable));

        verify(search).searchApproved("spring", pageable);
        verify(repository, never()).searchPublicVideos(any(), any(), any());
    }

    @Test
    void blankPublicSearchStillUsesRecommendedVideos() {
        var pageable = PageRequest.of(0, 12);
        var result = new PageImpl<>(List.of(video()), pageable, 1);
        when(repository.findAllByStatusOrderByViewsDescUploadDateDescIdDesc(VideoStatus.APPROVED, pageable))
                .thenReturn(result);

        assertEquals(result, service.searchPublicVideos("   ", pageable));

        verifyNoInteractions(search);
    }

    @Test
    void searchUnavailableBubblesUpForControllerMapping() {
        var pageable = PageRequest.of(0, 12);
        when(search.searchApproved("spring", pageable))
                .thenThrow(new VideoSearchUnavailableException("down"));

        assertThrows(VideoSearchUnavailableException.class,
                () -> service.searchPublicVideos("spring", pageable));
    }

    private static Video video() {
        Video video = new Video();
        video.setId(10L);
        video.setStatus(VideoStatus.APPROVED);
        video.setTitle("Spring");
        return video;
    }
}
