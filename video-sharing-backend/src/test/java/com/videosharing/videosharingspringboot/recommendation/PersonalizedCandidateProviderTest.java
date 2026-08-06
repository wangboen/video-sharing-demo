package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.model.WatchHistoryId;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import com.videosharing.videosharingspringboot.repository.WatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalizedCandidateProviderTest {
    private final VideoRepository videoRepository = mock(VideoRepository.class);
    private final WatchHistoryRepository historyRepository = mock(WatchHistoryRepository.class);
    private final RecommendationCache cache = mock(RecommendationCache.class);
    private final InterestProfileService profiles = mock(InterestProfileService.class);

    @Test
    void categoryInterestProviderUsesTopProfileCategories() {
        RecommendationRequest request = RecommendationRequest.home(9L, 0, 12, null, null, true, true);
        when(profiles.categoryProfile(9L)).thenReturn(new InterestProfile(List.of(new WeightedId(7L, 4.0))));
        when(cache.getIdList("rec:pool:category:7:hot:limit:20")).thenReturn(Optional.empty());
        when(videoRepository.findCategoryCandidateIds(VideoStatus.APPROVED, 7L, PageRequest.of(0, 20)))
                .thenReturn(List.of(1L, 2L));
        CategoryInterestCandidateProvider provider = new CategoryInterestCandidateProvider(videoRepository, cache,
                profiles, 300);

        CandidatePoolResult result = provider.candidates(request, 20);

        assertThat(result.type()).isEqualTo(CandidatePoolType.CATEGORY_INTEREST);
        assertThat(result.videoIds()).containsExactly(1L, 2L);
        verify(cache).putIdList("rec:pool:category:7:hot:limit:20", List.of(1L, 2L), Duration.ofSeconds(300));
    }

    @Test
    void tagInterestProviderFallsBackToRepositoryForTopTags() {
        RecommendationRequest request = RecommendationRequest.home(9L, 0, 12, null, null, true, true);
        when(profiles.tagProfile(9L)).thenReturn(new InterestProfile(List.of(new WeightedId(11L, 4.0))));
        when(cache.getIdList("rec:pool:tag:11:hot:limit:20")).thenReturn(Optional.empty());
        when(videoRepository.findTagCandidateIds(VideoStatus.APPROVED, 11L, PageRequest.of(0, 20)))
                .thenReturn(List.of(3L, 4L));
        TagInterestCandidateProvider provider = new TagInterestCandidateProvider(videoRepository, cache, profiles, 600);

        CandidatePoolResult result = provider.candidates(request, 20);

        assertThat(result.type()).isEqualTo(CandidatePoolType.TAG_INTEREST);
        assertThat(result.videoIds()).containsExactly(3L, 4L);
    }

    @Test
    void continueWatchingProviderReturnsCachedIncompleteVideos() {
        RecommendationRequest request = RecommendationRequest.home(9L, 0, 12, null, null, true, true);
        when(cache.getIdList("rec:user:9:continue_watching")).thenReturn(Optional.empty());
        WatchHistory first = history(100L);
        WatchHistory second = history(200L);
        when(historyRepository.findContinueWatchingCandidates(9L, VideoStatus.APPROVED, PageRequest.of(0, 10)))
                .thenReturn(List.of(first, second));
        ContinueWatchingCandidateProvider provider = new ContinueWatchingCandidateProvider(historyRepository, cache, 60);

        CandidatePoolResult result = provider.candidates(request, 10);

        assertThat(result.type()).isEqualTo(CandidatePoolType.CONTINUE_WATCHING);
        assertThat(result.videoIds()).containsExactly(100L, 200L);
        verify(cache).putIdList("rec:user:9:continue_watching", List.of(100L, 200L), Duration.ofSeconds(60));
    }

    private static WatchHistory history(Long videoId) {
        WatchHistory history = new WatchHistory();
        history.setId(new WatchHistoryId(9L, videoId));
        return history;
    }
}
