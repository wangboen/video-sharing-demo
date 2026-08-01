package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoSearchServiceTest {
    private final ElasticsearchOperations elasticsearch = mock(ElasticsearchOperations.class);
    private final VideoRepository repository = mock(VideoRepository.class);
    private final VideoSearchService service = new VideoSearchService(elasticsearch, repository, "videos-test");

    @Test
    @SuppressWarnings("unchecked")
    void searchApprovedPreservesElasticsearchOrderAndFiltersDatabaseStatus() {
        Video first = video(1L, VideoStatus.APPROVED);
        Video second = video(2L, VideoStatus.APPROVED);
        Video rejected = video(3L, VideoStatus.REJECTED);
        SearchHits<VideoSearchDocument> hits = mock(SearchHits.class);
        SearchHit<VideoSearchDocument> secondHit = mock(SearchHit.class);
        SearchHit<VideoSearchDocument> firstHit = mock(SearchHit.class);
        SearchHit<VideoSearchDocument> rejectedHit = mock(SearchHit.class);
        when(secondHit.getContent()).thenReturn(VideoSearchDocument.from(second));
        when(firstHit.getContent()).thenReturn(VideoSearchDocument.from(first));
        when(rejectedHit.getContent()).thenReturn(VideoSearchDocument.from(rejected));
        when(hits.getSearchHits()).thenReturn(List.of(secondHit, firstHit, rejectedHit));
        when(hits.getTotalHits()).thenReturn(3L);
        when(elasticsearch.search(any(Query.class), eq(VideoSearchDocument.class), any(IndexCoordinates.class)))
                .thenReturn(hits);
        when(repository.findAllById(List.of(2L, 1L, 3L))).thenReturn(List.of(first, rejected, second));

        var result = service.searchApproved("spring", PageRequest.of(0, 12));

        assertEquals(List.of(2L, 1L), result.getContent().stream().map(Video::getId).toList());
        assertEquals(3L, result.getTotalElements());
    }

    @Test
    void indexApprovedSavesOnlyApprovedVideos() {
        Video approved = video(1L, VideoStatus.APPROVED);
        Video pending = video(2L, VideoStatus.PENDING);

        service.indexApproved(approved);
        service.indexApproved(pending);

        verify(elasticsearch).save(any(VideoSearchDocument.class), any(IndexCoordinates.class));
    }

    @Test
    void deleteRemovesDocumentByVideoId() {
        service.delete(42L);

        verify(elasticsearch).delete(any(Query.class), eq(VideoSearchDocument.class),
                eq(IndexCoordinates.of("videos-test")));
    }

    @Test
    void searchFailureIsReportedAsUnavailable() {
        when(elasticsearch.search(any(Query.class), eq(VideoSearchDocument.class), any(IndexCoordinates.class)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThrows(VideoSearchUnavailableException.class,
                () -> service.searchApproved("spring", PageRequest.of(0, 12)));
    }

    private static Video video(Long id, VideoStatus status) {
        Video video = new Video();
        video.setId(id);
        video.setTitle("Spring " + id);
        video.setDescription("Description");
        video.setStatus(status);
        video.setFilePath("public/video-" + id + ".mp4");
        video.setThumbnailPath("public/thumb-" + id + ".jpg");
        video.setUploadDate(LocalDateTime.of(2026, 7, 24, 12, 0));
        video.setViews(10L);
        return video;
    }
}
