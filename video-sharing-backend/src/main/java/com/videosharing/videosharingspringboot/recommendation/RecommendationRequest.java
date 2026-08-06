package com.videosharing.videosharingspringboot.recommendation;

public record RecommendationRequest(
        Long userId,
        int page,
        int size,
        Long categoryId,
        Long tagId,
        boolean loggedIn,
        boolean richHistory) {

    public static RecommendationRequest home(Long userId,
                                             int page,
                                             int size,
                                             Long categoryId,
                                             Long tagId,
                                             boolean loggedIn,
                                             boolean richHistory) {
        return new RecommendationRequest(userId, Math.max(page, 0), Math.max(size, 1), categoryId, tagId,
                loggedIn, richHistory);
    }
}
