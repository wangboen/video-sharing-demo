package com.videosharing.videosharingspringboot.recommendation;

import java.util.List;

public record CandidatePoolResult(CandidatePoolType type, List<Long> videoIds) {
    public CandidatePoolResult {
        videoIds = videoIds == null ? List.of() : List.copyOf(videoIds);
    }
}
