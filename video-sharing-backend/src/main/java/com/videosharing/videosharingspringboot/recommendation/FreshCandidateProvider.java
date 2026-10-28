package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class FreshCandidateProvider {
    private final VideoRepository repository;
    private final RecommendationCache cache;
    private final Clock clock;
    private final long ttlSeconds;
    private final long windowDays;

    @Autowired
    public FreshCandidateProvider(VideoRepository repository,
                                  RecommendationCache cache,
                                  @Value("${recommendation.cache.fresh-ttl-seconds:300}") long ttlSeconds,
                                  @Value("${recommendation.fresh.window-days:7}") long windowDays) {
        this(repository, cache, Clock.systemUTC(), ttlSeconds, windowDays);
    }

    FreshCandidateProvider(VideoRepository repository,
                           RecommendationCache cache,
                           Clock clock,
                           long ttlSeconds,
                           long windowDays) {
        this.repository = repository;
        this.cache = cache;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
        this.windowDays = windowDays;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        String key = cacheKey(request, limit);
        List<Long> ids = cache.getIdList(key).orElseGet(() -> {
            LocalDateTime uploadedAfter = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                    .minusDays(windowDays);
            List<Long> loaded = request.categoryId() == null
                    ? repository.findFreshCandidateIds(VideoStatus.APPROVED, uploadedAfter, PageRequest.of(0, limit))
                    : repository.findFreshCandidateIdsByCategory(VideoStatus.APPROVED, request.categoryId(),
                            uploadedAfter, PageRequest.of(0, limit));
            cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
            return loaded;
        });
        return new CandidatePoolResult(CandidatePoolType.FRESH, ids);
    }

    String cacheKey(RecommendationRequest request, int limit) {
        if (request.categoryId() == null) {
            return "rec:pool:fresh:global:window:" + windowDays + "d:limit:" + limit;
        }
        return "rec:pool:fresh:category:" + request.categoryId() + ":window:" + windowDays + "d:limit:" + limit;
    }
}
