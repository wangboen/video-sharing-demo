package com.videosharing.videosharingspringboot.recommendation;

import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {
    private final VideoRepository repository = mock(VideoRepository.class);
    private final HotCandidateProvider hot = mock(HotCandidateProvider.class);
    private final FreshCandidateProvider fresh = mock(FreshCandidateProvider.class);
    private final ExploreCandidateProvider explore = mock(ExploreCandidateProvider.class);
    private final CategoryInterestCandidateProvider category = mock(CategoryInterestCandidateProvider.class);
    private final TagInterestCandidateProvider tag = mock(TagInterestCandidateProvider.class);
    private final ContinueWatchingCandidateProvider watching = mock(ContinueWatchingCandidateProvider.class);
    private final InterestProfileService profiles = mock(InterestProfileService.class);

    @Test
    void anonymousRecommendationUsesPublicPoolsAndPreservesMixedOrder() {
        RecommendationService service = service();
        var pageable = PageRequest.of(0, 12);
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, null, null, false, false);
        when(hot.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.HOT,
                List.of(1L, 2L, 3L, 4L, 5L, 6L)));
        when(fresh.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.FRESH,
                List.of(7L, 8L, 9L)));
        when(explore.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.EXPLORE,
                List.of(10L)));
        when(repository.findHotCandidateIds(VideoStatus.APPROVED, PageRequest.of(0, 50)))
                .thenReturn(List.of(11L, 12L, 13L));
        when(repository.findAllByIdIn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L)))
                .thenReturn(videos(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L));

        Page<Video> result = service.recommend(null, pageable, null, null);

        assertThat(result.getContent()).extracting(Video::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
    }

    @Test
    void loggedInRecommendationIncludesPersonalizedPoolsAndSurvivesProviderFailure() {
        RecommendationService service = service();
        User user = new User();
        user.setId(9L);
        var pageable = PageRequest.of(0, 12);
        RecommendationRequest request = RecommendationRequest.home(9L, 0, 12, null, null, true, true);
        when(profiles.categoryProfile(9L)).thenReturn(new InterestProfile(List.of(
                new WeightedId(7L, 5.0), new WeightedId(8L, 4.0), new WeightedId(9L, 3.0))));
        when(profiles.tagProfile(9L)).thenReturn(new InterestProfile(List.of(new WeightedId(11L, 5.0))));
        when(category.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.CATEGORY_INTEREST,
                List.of(1L, 2L, 3L)));
        when(tag.candidates(request, 36)).thenThrow(new RuntimeException("tag backend down"));
        when(hot.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.HOT, List.of(4L, 5L)));
        when(fresh.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.FRESH, List.of(6L)));
        when(watching.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.CONTINUE_WATCHING,
                List.of(7L)));
        when(explore.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of(8L)));
        when(repository.findHotCandidateIds(VideoStatus.APPROVED, PageRequest.of(0, 50)))
                .thenReturn(List.of(9L, 10L, 11L, 12L));
        when(repository.findAllByIdIn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L)))
                .thenReturn(videos(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L));

        Page<Video> result = service.recommend(user, pageable, null, null);

        assertThat(result.getContent()).extracting(Video::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
    }

    @Test
    void tagFilterPreservesExistingRepositoryBehavior() {
        RecommendationService service = service();
        var pageable = PageRequest.of(0, 12);
        var expected = new PageImpl<>(videos(42L), pageable, 1);
        when(repository.findRecommendedByTag(VideoStatus.APPROVED, 5L, pageable)).thenReturn(expected);

        assertThat(service.recommend(null, pageable, null, 5L)).isEqualTo(expected);
    }

    @Test
    void categoryRecommendationUsesCategoryScopedHotFallback() {
        RecommendationService service = service();
        var pageable = PageRequest.of(0, 12);
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, 7L, null, false, false);
        when(hot.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.HOT, List.of(1L)));
        when(fresh.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.FRESH, List.of()));
        when(explore.candidates(request, 36)).thenReturn(new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of()));
        when(repository.findHotCandidateIdsByCategory(VideoStatus.APPROVED, 7L, PageRequest.of(0, 50)))
                .thenReturn(List.of(2L, 3L));
        when(repository.findAllByIdIn(List.of(1L, 2L, 3L))).thenReturn(videos(3L, 2L, 1L));

        Page<Video> result = service.recommend(null, pageable, 7L, null);

        assertThat(result.getContent()).extracting(Video::getId).containsExactly(1L, 2L, 3L);
    }

    private RecommendationService service() {
        return new RecommendationService(repository, hot, fresh, explore, category, tag, watching, profiles,
                new RecommendationMixer());
    }

    private static List<Video> videos(Long... ids) {
        return java.util.Arrays.stream(ids).map(id -> {
            Video video = new Video();
            video.setId(id);
            video.setStatus(VideoStatus.APPROVED);
            video.setTitle("Video " + id);
            return video;
        }).toList();
    }
}
