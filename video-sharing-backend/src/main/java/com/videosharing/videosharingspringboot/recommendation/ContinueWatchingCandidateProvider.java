package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ContinueWatchingCandidateProvider {
    private final WatchHistoryRepository historyRepository;
    private final RecommendationCache cache;
    private final long ttlSeconds;

    public ContinueWatchingCandidateProvider(WatchHistoryRepository historyRepository,
                                             RecommendationCache cache,
                                             @Value("${recommendation.cache.continue-ttl-seconds:60}") long ttlSeconds) {
        this.historyRepository = historyRepository;
        this.cache = cache;
        this.ttlSeconds = ttlSeconds;
    }

    public CandidatePoolResult candidates(RecommendationRequest request, int limit) {
        if (request.userId() == null) {
            return new CandidatePoolResult(CandidatePoolType.CONTINUE_WATCHING, List.of());
        }
        String key = "rec:user:" + request.userId() + ":continue_watching";
        List<Long> ids = cache.getIdList(key).orElseGet(() -> {
            List<Long> loaded = historyRepository.findContinueWatchingCandidates(request.userId(),
                            VideoStatus.APPROVED, PageRequest.of(0, limit))
                    .stream()
                    .map(record -> record.getId().getVideoId())
                    .toList();
            cache.putIdList(key, loaded, Duration.ofSeconds(ttlSeconds));
            return loaded;
        });
        return new CandidatePoolResult(CandidatePoolType.CONTINUE_WATCHING, ids);
    }
}
