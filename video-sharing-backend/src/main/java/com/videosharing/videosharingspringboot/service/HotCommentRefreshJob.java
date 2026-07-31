package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.config.CommentProperties;
import com.videosharing.videosharingspringboot.repository.CommentRelationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HotCommentRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(HotCommentRefreshJob.class);

    private final HotCommentCacheService cache;
    private final CommentRelationRepository relations;
    private final CommentProperties properties;

    public HotCommentRefreshJob(HotCommentCacheService cache, CommentRelationRepository relations,
                                CommentProperties properties) {
        this.cache = cache;
        this.relations = relations;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshHotComments() {
        for (Long videoId : cache.popRefreshCandidates(properties.getHotRefreshBatchSize())) {
            try {
                var hotRelations = relations.findTopHotVisible(videoId, properties.getFeaturedMinLikes(),
                        PageRequest.of(0, properties.getFeaturedLimit()));
                cache.putHotCommentIds(videoId, properties.getFeaturedLimit(), properties.getFeaturedMinLikes(),
                        hotRelations.stream().map(relation -> relation.getCommentId()).toList());
            } catch (RuntimeException exception) {
                log.warn("Failed to refresh hot comment cache for videoId={}", videoId, exception);
            }
        }
    }
}
