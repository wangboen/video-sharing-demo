package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class HotCandidateProvider {
    private final VideoRepository repository;
    private final RecommendationCache cache;
    private final long ttlSeconds;

    public HotCandidateProvider(VideoRepository repository,
                                RecommendationCache cache,
                                @Value("${recommendation.cache.hot-ttl-seconds:60}") long ttlSeconds) {
        this.repository = repository;
        this.cache = cache;
        this.ttlSeconds = ttlSeconds;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        String key = cacheKey(request, limit);
        List<Long> ids = cache.getIdList(key).orElseGet(() -> {
            List<Long> loaded = request.categoryId() == null
                    ? repository.findHotCandidateIds(VideoStatus.APPROVED, PageRequest.of(request.page(), limit))
                    : repository.findHotCandidateIdsByCategory(VideoStatus.APPROVED, request.categoryId(),
                            PageRequest.of(request.page(), limit));
            cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
            return loaded;
        });
        return new CandidatePoolResult(CandidatePoolType.HOT, ids);
    }

    String cacheKey(RecommendationRequest request, int limit) {
        if (request.categoryId() == null) {
            return "rec:pool:hot:global:page:" + request.page() + ":size:" + limit;
        }
        return "rec:pool:hot:category:" + request.categoryId() + ":page:" + request.page() + ":size:" + limit;
    }
}
