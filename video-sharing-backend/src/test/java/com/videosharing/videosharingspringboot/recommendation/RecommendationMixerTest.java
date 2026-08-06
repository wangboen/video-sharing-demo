package com.videosharing.videosharingspringboot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationMixerTest {

    @Test
    void mixesLoggedInRichHistoryWithDedupeAndHotFallback() {
        RecommendationRequest request = RecommendationRequest.home(10L, 0, 12, null, null, true, true);
        List<CandidatePoolResult> pools = List.of(
                new CandidatePoolResult(CandidatePoolType.CATEGORY_INTEREST, List.of(1L, 2L, 3L)),
                new CandidatePoolResult(CandidatePoolType.TAG_INTEREST, List.of(3L, 4L, 5L)),
                new CandidatePoolResult(CandidatePoolType.HOT, List.of(5L, 6L, 7L)),
                new CandidatePoolResult(CandidatePoolType.FRESH, List.of(8L, 9L)),
                new CandidatePoolResult(CandidatePoolType.CONTINUE_WATCHING, List.of(10L)),
                new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of(11L))
        );

        List<Long> mixed = new RecommendationMixer().mix(request, pools, List.of(12L, 13L, 14L));

        assertThat(mixed).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L, 10L, 11L, 12L, 13L);
    }

    @Test
    void mixesAnonymousTrafficWithoutInterestPools() {
        RecommendationRequest request = RecommendationRequest.home(null, 0, 12, null, null, false, false);
        List<CandidatePoolResult> pools = List.of(
                new CandidatePoolResult(CandidatePoolType.HOT, List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                new CandidatePoolResult(CandidatePoolType.FRESH, List.of(7L, 8L, 9L)),
                new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of(10L))
        );

        List<Long> mixed = new RecommendationMixer().mix(request, pools, List.of(11L, 12L, 13L));

        assertThat(mixed).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
    }
}
