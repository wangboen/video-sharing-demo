package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VideoSearchService {
    private final ElasticsearchOperations elasticsearch;
    private final VideoRepository videos;
    private final IndexCoordinates index;

    public VideoSearchService(ElasticsearchOperations elasticsearch,
                              VideoRepository videos,
                              @Value("${video-search.index-name:videos}") String indexName) {
        this.elasticsearch = elasticsearch;
        this.videos = videos;
        this.index = IndexCoordinates.of(indexName);
    }

    public Page<Video> searchApproved(String query, Pageable pageable) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return Page.empty(pageable);
        }
        try {
            NativeQuery search = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.multiMatch(mm -> mm
                                    .query(trimmed)
                                    .fields("title^2", "description")))
                            .filter(f -> f.term(t -> t
                                    .field("status")
                                    .value(VideoStatus.APPROVED.name())))))
                    .withPageable(pageable)
                    .withSort(Sort.by(
                            Sort.Order.desc("_score"),
                            Sort.Order.desc("uploadDate"),
                            Sort.Order.desc("id")))
                    .build();
            SearchHits<VideoSearchDocument> hits = elasticsearch.search(search, VideoSearchDocument.class, index);
            List<Long> ids = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(VideoSearchDocument::getId)
                    .toList();
            return new FixedTotalPage<>(orderedApprovedVideos(ids), pageable, hits.getTotalHits());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public void indexApproved(Video video) {
        if (video == null || video.getId() == null || video.getStatus() != VideoStatus.APPROVED) {
            return;
        }
        try {
            elasticsearch.save(VideoSearchDocument.from(video), index);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public void delete(Long videoId) {
        if (videoId == null) {
            return;
        }
        try {
            NativeQuery delete = NativeQuery.builder()
                    .withQuery(q -> q.ids(ids -> ids.values(String.valueOf(videoId))))
                    .build();
            elasticsearch.delete(delete, VideoSearchDocument.class, index);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private List<Video> orderedApprovedVideos(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Video> byId = new HashMap<>();
        videos.findAllById(ids).forEach(video -> {
            if (video.getStatus() == VideoStatus.APPROVED) {
                byId.put(video.getId(), video);
            }
        });
        List<Video> ordered = new ArrayList<>();
        for (Long id : ids) {
            Video video = byId.get(id);
            if (video != null) {
                ordered.add(video);
            }
        }
        return ordered;
    }

    private VideoSearchUnavailableException unavailable(RuntimeException exception) {
        if (exception instanceof VideoSearchUnavailableException unavailable) {
            return unavailable;
        }
        if (exception instanceof DataAccessResourceFailureException
                || exception instanceof UncategorizedElasticsearchException) {
            return new VideoSearchUnavailableException("Elasticsearch unavailable", exception);
        }
        return new VideoSearchUnavailableException("Elasticsearch search failed", exception);
    }

    private static final class FixedTotalPage<T> extends PageImpl<T> {
        private final long total;

        private FixedTotalPage(List<T> content, Pageable pageable, long total) {
            super(content, pageable, total);
            this.total = total;
        }

        @Override
        public long getTotalElements() {
            return total;
        }

        @Override
        public int getTotalPages() {
            int size = getSize();
            return size == 0 ? 1 : (int) Math.ceil((double) total / (double) size);
        }
    }
}
