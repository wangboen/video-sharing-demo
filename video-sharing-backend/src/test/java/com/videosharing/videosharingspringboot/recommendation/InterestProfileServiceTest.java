package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoCategory;
import com.videosharing.videosharingspringboot.model.VideoTag;
import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterestProfileServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC);

    private final WatchHistoryRepository historyRepository = mock(WatchHistoryRepository.class);
    private final RecommendationCache cache = mock(RecommendationCache.class);

    @Test
    void categoryProfileScoresCompletionProgressAndRecencyThenKeepsTopThree() {
        when(cache.getString("rec:user:9:category_profile")).thenReturn(Optional.empty());
        when(historyRepository.findTop20ByIdUserIdOrderByLastWatchedAtDesc(9L)).thenReturn(List.of(
                history(1L, List.of(101L), true, "95.00", 1),
                history(2L, List.of(201L), false, "55.00", 2),
                history(3L, List.of(301L), false, "20.00", 20),
                history(4L, List.of(401L), true, "95.00", 1)
        ));
        InterestProfileService service = new InterestProfileService(historyRepository, cache, CLOCK, 600);

        InterestProfile profile = service.categoryProfile(9L);

        assertThat(profile.items()).extracting(WeightedId::id).containsExactly(1L, 4L, 2L);
        assertThat(profile.items().get(0).weight()).isGreaterThan(profile.items().get(2).weight());
    }

    @Test
    void tagProfileKeepsTopTenAndReadsCacheWhenPresent() {
        when(cache.getString("rec:user:9:tag_profile")).thenReturn(Optional.of("11:5.0,12:3.0"));
        InterestProfileService service = new InterestProfileService(historyRepository, cache, CLOCK, 600);

        InterestProfile profile = service.tagProfile(9L);

        assertThat(profile.items()).containsExactly(new WeightedId(11L, 5.0), new WeightedId(12L, 3.0));
    }

    private static WatchHistory history(Long categoryId,
                                        List<Long> tagIds,
                                        boolean completed,
                                        String progress,
                                        int watchedDaysAgo) {
        VideoCategory category = new VideoCategory();
        category.setId(categoryId);
        Video video = new Video();
        video.setId(categoryId * 10);
        video.setCategory(category);
        LinkedHashSet<VideoTag> tags = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            VideoTag tag = new VideoTag();
            tag.setId(tagId);
            tag.setCategory(category);
            tags.add(tag);
        }
        video.setTags(tags);

        WatchHistory history = new WatchHistory();
        history.setVideo(video);
        history.setCompleted(completed);
        history.setProgressPercent(new BigDecimal(progress));
        history.setLastWatchedAt(CLOCK.instant().minus(Duration.ofDays(watchedDaysAgo)));
        return history;
    }
}
