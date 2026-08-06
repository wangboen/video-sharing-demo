package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
public class ExploreCandidateProvider {
    private final VideoRepository repository;
    private final RecommendationCache cache;
    private final Clock clock;
    private final long ttlSeconds;
    private final int bucketCount;
    private final long maxViews;
    private final long windowDays;

    public ExploreCandidateProvider(VideoRepository repository,
                                    RecommendationCache cache,
                                    @Value("${recommendation.cache.explore-ttl-seconds:1800}") long ttlSeconds,
                                    @Value("${recommendation.explore.bucket-count:8}") int bucketCount,
                                    @Value("${recommendation.explore.max-views:100}") long maxViews,
                                    @Value("${recommendation.explore.window-days:30}") long windowDays) {
        this(repository, cache, Clock.systemUTC(), ttlSeconds, bucketCount, maxViews, windowDays);
    }

    ExploreCandidateProvider(VideoRepository repository,
                             RecommendationCache cache,
                             Clock clock,
                             long ttlSeconds,
                             int bucketCount,
                             long maxViews,
                             long windowDays) {
        this.repository = repository;
        this.cache = cache;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
        this.bucketCount = Math.max(bucketCount, 1);
        this.maxViews = maxViews;
        this.windowDays = windowDays;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        String key = cacheKey(request, limit);
        List<Long> ids = cache.getIdList(key).orElseGet(() -> {
            LocalDateTime uploadedAfter = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                    .minusDays(windowDays);
            List<Long> loaded = repository.findExploreCandidateIds(VideoStatus.APPROVED, uploadedAfter, maxViews,
                    PageRequest.of(0, limit));
            cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
            return loaded;
        });
        return new CandidatePoolResult(CandidatePoolType.EXPLORE, ids);
    }

    public String cacheKey(RecommendationRequest request, int limit) {
        int bucket = bucket(request);
        if (request.categoryId() == null) {
            return "rec:pool:explore:bucket:" + bucket + ":limit:" + limit;
        }
        return "rec:pool:explore:category:" + request.categoryId() + ":bucket:" + bucket + ":limit:" + limit;
    }

    private int bucket(RecommendationRequest request) {
        String userPart = request.userId() == null ? "anonymous" : request.userId().toString();
        LocalDate day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return Math.floorMod(Objects.hash(userPart, day), bucketCount);
    }
}
