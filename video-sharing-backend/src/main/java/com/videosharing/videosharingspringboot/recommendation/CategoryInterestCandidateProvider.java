package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryInterestCandidateProvider {
    private final VideoRepository repository;
    private final RecommendationCache cache;
    private final InterestProfileService profiles;
    private final long ttlSeconds;

    public CategoryInterestCandidateProvider(VideoRepository repository,
                                             RecommendationCache cache,
                                             InterestProfileService profiles,
                                             @Value("${recommendation.cache.fresh-ttl-seconds:300}") long ttlSeconds) {
        this.repository = repository;
        this.cache = cache;
        this.profiles = profiles;
        this.ttlSeconds = ttlSeconds;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        List<Long> ids = new ArrayList<>();
        for (WeightedId item : profiles.categoryProfile(request.userId()).items()) {
            String key = "rec:pool:category:" + item.id() + ":hot:limit:" + limit;
            List<Long> categoryIds = cache.getIdList(key).orElseGet(() -> {
                List<Long> loaded = repository.findCategoryCandidateIds(VideoStatus.APPROVED, item.id(),
                        PageRequest.of(0, limit));
                cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
                return loaded;
            });
            ids.addAll(categoryIds);
        }
        return new CandidatePoolResult(CandidatePoolType.CATEGORY_INTEREST, ids);
    }
}
