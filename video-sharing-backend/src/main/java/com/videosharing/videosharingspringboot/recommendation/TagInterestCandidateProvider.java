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
public class TagInterestCandidateProvider {
    private final VideoRepository repository;
    private final RecommendationCache cache;
    private final InterestProfileService profiles;
    private final long ttlSeconds;

    public TagInterestCandidateProvider(VideoRepository repository,
                                        RecommendationCache cache,
                                        InterestProfileService profiles,
                                        @Value("${recommendation.cache.profile-ttl-seconds:600}") long ttlSeconds) {
        this.repository = repository;
        this.cache = cache;
        this.profiles = profiles;
        this.ttlSeconds = ttlSeconds;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        List<Long> ids = new ArrayList<>();
        profiles.tagProfile(request.userId()).items().stream()
                .limit(5)
                .forEach(item -> ids.addAll(tagCandidates(item.id(), limit)));
        return new CandidatePoolResult(CandidatePoolType.TAG_INTEREST, ids);
    }

    private List<Long> tagCandidates(Long tagId, int limit) {
        String key = "rec:pool:tag:" + tagId + ":hot:limit:" + limit;
        return cache.getIdList(key).orElseGet(() -> {
            List<Long> loaded = repository.findTagCandidateIds(VideoStatus.APPROVED, tagId, PageRequest.of(0, limit));
            cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
            return loaded;
        });
    }
}
