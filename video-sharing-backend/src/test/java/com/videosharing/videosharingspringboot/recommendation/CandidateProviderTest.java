package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC);

    private final VideoRepository repository = mock(VideoRepository.class);
    private final RecommendationCache cache = mock(RecommendationCache.class);

    @Test
    void hotProviderUsesCachedCategoryIdsWhenPresent() {
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, 7L, null, false, false);
        when(cache.getIdList("rec:pool:hot:category:7:page:0:size:30")).thenReturn(Optional.of(List.of(3L, 2L)));
        HotCandidateProvider provider = new HotCandidateProvider(repository, cache, 60);

        CandidatePoolResult result = provider.candidates(request, 30);

        assertThat(result.type()).isEqualTo(CandidatePoolType.HOT);
        assertThat(result.videoIds()).containsExactly(3L, 2L);
        verify(repository, never()).findHotCandidateIdsByCategory(any(), any(), any());
    }

    @Test
    void hotProviderCachesRepositoryMissesIncludingEmptyResults() {
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, null, null, false, false);
        when(cache.getIdList("rec:pool:hot:global:page:0:size:30")).thenReturn(Optional.empty());
        when(repository.findHotCandidateIds(VideoStatus.APPROVED, PageRequest.of(0, 30))).thenReturn(List.of());
        HotCandidateProvider provider = new HotCandidateProvider(repository, cache, 60);

        CandidatePoolResult result = provider.candidates(request, 30);

        assertThat(result.videoIds()).isEmpty();
        verify(cache).putIdList("rec:pool:hot:global:page:0:size:30", List.of(), Duration.ofSeconds(60));
    }

    @Test
    void freshProviderUsesSevenDayWindowAndCategoryKey() {
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, 4L, null, false, false);
        when(cache.getIdList("rec:pool:fresh:category:4:window:7d:limit:20")).thenReturn(Optional.empty());
        when(repository.findFreshCandidateIdsByCategory(eq(VideoStatus.APPROVED), eq(4L), any(), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(9L, 8L));
        FreshCandidateProvider provider = new FreshCandidateProvider(repository, cache, CLOCK, 300, 7);

        CandidatePoolResult result = provider.candidates(request, 20);

        assertThat(result.type()).isEqualTo(CandidatePoolType.FRESH);
        assertThat(result.videoIds()).containsExactly(9L, 8L);
        verify(cache).putIdList("rec:pool:fresh:category:4:window:7d:limit:20", List.of(9L, 8L),
                Duration.ofSeconds(300));
    }

    @Test
    void exploreProviderUsesStableUserBucketAndCachesResults() {
        RecommendationRequest request = RecommendationRequest.home(42L, 0, 12, null, null, true, false);
        ExploreCandidateProvider provider = new ExploreCandidateProvider(repository, cache, CLOCK, 1800, 8, 100, 30);
        String key = provider.cacheKey(request, 20);
        when(cache.getIdList(key)).thenReturn(Optional.empty());
        when(repository.findExploreCandidateIds(eq(VideoStatus.APPROVED), any(), eq(100L), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(11L));

        CandidatePoolResult result = provider.candidates(request, 20);

        assertThat(key).startsWith("rec:pool:explore:bucket:");
        assertThat(result.type()).isEqualTo(CandidatePoolType.EXPLORE);
        assertThat(result.videoIds()).containsExactly(11L);
        verify(cache).putIdList(key, List.of(11L), Duration.ofSeconds(1800));
    }
}
