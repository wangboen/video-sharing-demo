package com.videosharing.videosharingspringboot.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HotCommentCacheService {
    public List<Long> getHotCommentIds(Long videoId, int limit, long minLikes) {
        return List.of();
    }
}
