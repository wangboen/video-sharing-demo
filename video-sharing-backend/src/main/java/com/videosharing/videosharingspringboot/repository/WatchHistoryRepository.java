package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.model.WatchHistoryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, WatchHistoryId> {
    Optional<WatchHistory> findByIdUserIdAndIdVideoId(Long userId, Long videoId);

    @EntityGraph(attributePaths = {"video", "video.user"})
    Page<WatchHistory> findAllByIdUserIdOrderByLastWatchedAtDesc(Long userId, Pageable pageable);

    void deleteByIdUserIdAndIdVideoId(Long userId, Long videoId);

    void deleteByIdUserId(Long userId);
}
