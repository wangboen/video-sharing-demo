package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.WatchHistory;
import com.videosharing.videosharingspringboot.model.WatchHistoryId;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, WatchHistoryId> {
    Optional<WatchHistory> findByIdUserIdAndIdVideoId(Long userId, Long videoId);

    @EntityGraph(attributePaths = {"video", "video.user"})
    Page<WatchHistory> findAllByIdUserIdOrderByLastWatchedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"video", "video.category", "video.tags"})
    List<WatchHistory> findTop20ByIdUserIdOrderByLastWatchedAtDesc(Long userId);

    @Query("""
            select h from WatchHistory h
            join fetch h.video v
            where h.id.userId = :userId
              and h.completed = false
              and h.progressPercent >= 5
              and h.progressPercent < 95
              and v.status = :status
            order by h.lastWatchedAt desc
            """)
    List<WatchHistory> findContinueWatchingCandidates(@Param("userId") Long userId,
                                                      @Param("status") VideoStatus status,
                                                      Pageable pageable);

    @EntityGraph(attributePaths = {"video", "video.user"})
    Page<WatchHistory> findAllByIdUserIdAndLastWatchedAtBetweenOrderByLastWatchedAtDesc(
            Long userId, Instant from, Instant to, Pageable pageable);

    void deleteByIdUserIdAndIdVideoId(Long userId, Long videoId);

    void deleteByIdUserId(Long userId);
}
