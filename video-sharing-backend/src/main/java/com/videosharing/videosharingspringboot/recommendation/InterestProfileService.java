package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoTag;
import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InterestProfileService {
    private final WatchHistoryRepository historyRepository;
    private final RecommendationCache cache;
    private final Clock clock;
    private final long ttlSeconds;

    @Autowired
    public InterestProfileService(WatchHistoryRepository historyRepository,
                                  RecommendationCache cache,
                                  @Value("${recommendation.cache.profile-ttl-seconds:600}") long ttlSeconds) {
        this(historyRepository, cache, Clock.systemUTC(), ttlSeconds);
    }

    InterestProfileService(WatchHistoryRepository historyRepository,
                           RecommendationCache cache,
                           Clock clock,
                           long ttlSeconds) {
        this.historyRepository = historyRepository;
        this.cache = cache;
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
    }

    public InterestProfile categoryProfile(Long userId) {
        if (userId == null) {
            return new InterestProfile(List.of());
        }
        String key = "rec:user:" + userId + ":category_profile";
        return cache.getString(key)
                .map(InterestProfileService::parseProfile)
                .orElseGet(() -> {
                    InterestProfile profile = buildCategoryProfile(userId);
                    cache.putString(key, serialize(profile), Duration.ofSeconds(ttlSeconds));
                    return profile;
                });
    }

    public InterestProfile tagProfile(Long userId) {
        if (userId == null) {
            return new InterestProfile(List.of());
        }
        String key = "rec:user:" + userId + ":tag_profile";
        return cache.getString(key)
                .map(InterestProfileService::parseProfile)
                .orElseGet(() -> {
                    InterestProfile profile = buildTagProfile(userId);
                    cache.putString(key, serialize(profile), Duration.ofSeconds(ttlSeconds));
                    return profile;
                });
    }

    private InterestProfile buildCategoryProfile(Long userId) {
        Map<Long, Double> scores = new HashMap<>();
        for (WatchHistory record : historyRepository.findTop20ByIdUserIdOrderByLastWatchedAtDesc(userId)) {
            if (record.getVideo() == null || record.getVideo().getCategory() == null) {
                continue;
            }
            scores.merge(record.getVideo().getCategory().getId(), score(record), Double::sum);
        }
        return profile(scores, 3);
    }

    private InterestProfile buildTagProfile(Long userId) {
        Map<Long, Double> scores = new HashMap<>();
        for (WatchHistory record : historyRepository.findTop20ByIdUserIdOrderByLastWatchedAtDesc(userId)) {
            if (record.getVideo() == null || record.getVideo().getTags() == null) {
                continue;
            }
            double score = score(record);
            for (VideoTag tag : record.getVideo().getTags()) {
                scores.merge(tag.getId(), score, Double::sum);
            }
        }
        return profile(scores, 10);
    }

    private InterestProfile profile(Map<Long, Double> scores, int limit) {
        return new InterestProfile(scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> new WeightedId(entry.getKey(), entry.getValue()))
                .toList());
    }

    private double score(WatchHistory record) {
        double score = 1.0;
        if (record.isCompleted()) {
            score += 2.0;
        }
        BigDecimal progress = record.getProgressPercent();
        if (progress != null && progress.compareTo(new BigDecimal("50.00")) >= 0) {
            score += 1.0;
        }
        Instant lastWatchedAt = record.getLastWatchedAt();
        if (lastWatchedAt != null && !lastWatchedAt.isBefore(clock.instant().minus(Duration.ofDays(7)))) {
            score += 1.0;
        }
        return score;
    }

    private static String serialize(InterestProfile profile) {
        return profile.items().stream()
                .map(item -> item.id() + ":" + item.weight())
                .collect(Collectors.joining(","));
    }

    private static InterestProfile parseProfile(String value) {
        if (value == null || value.isBlank()) {
            return new InterestProfile(List.of());
        }
        return new InterestProfile(List.of(value.split(",")).stream()
                .filter(part -> !part.isBlank())
                .map(part -> {
                    String[] pieces = part.split(":", 2);
                    return new WeightedId(Long.valueOf(pieces[0]), Double.parseDouble(pieces[1]));
                })
                .toList());
    }
}
