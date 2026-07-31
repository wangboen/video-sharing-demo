package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus status);
    Optional<Video> findByIdAndStatus(Long id, VideoStatus status);
    List<Video> findAllByUserIdOrderByUploadDateDescIdDesc(Long userId);
    Optional<Video> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.id = :id")
    Optional<Video> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select v from Video v
            where v.leaseOwner.id = :ownerId and v.status = :status
              and v.leaseExpiresAt > :now
            order by v.uploadDate asc, v.id asc
            """)
    Optional<Video> findActiveLeaseForOwner(@Param("ownerId") Long ownerId,
                                            @Param("status") VideoStatus status,
                                            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from Video v
            where v.status = :status
              and (v.leaseOwner is null or v.leaseExpiresAt <= :now)
            order by v.uploadDate asc, v.id asc
            """)
    List<Video> findClaimable(@Param("status") VideoStatus status,
                              @Param("now") Instant now,
                              Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from Video v
            where v.status = :status
              and (v.leaseOwner is null or v.leaseExpiresAt <= :now)
            order by v.uploadDate asc, v.id asc
            """)
    List<Video> findClaimableForUpdate(@Param("status") VideoStatus status,
                                       @Param("now") Instant now,
                                       Pageable pageable);

    @Query("""
            select v from Video v
            where v.status = :status
              and v.reviewedAt < :expiredBefore
              and ((v.filePath is not null and v.fileDeletedAt is null)
                or (v.thumbnailPath is not null and v.thumbnailDeletedAt is null))
            order by v.reviewedAt asc, v.id asc
            """)
    List<Video> findRejectedWithDownloadableObjectsBefore(@Param("status") VideoStatus status,
                                                          @Param("expiredBefore") Instant expiredBefore,
                                                          Pageable pageable);

    @Query("""
            select v.reviewer.id as reviewerId,
                   count(v.id) as reviewedTotal,
                   sum(case when v.status = :approved then 1 else 0 end) as approvedTotal,
                   sum(case when v.status = :rejected then 1 else 0 end) as rejectedTotal,
                   max(v.reviewedAt) as lastReviewedAt
            from Video v
            where v.reviewer.id in :reviewerIds
              and v.status in :statuses
            group by v.reviewer.id
            """)
    List<ReviewerStatsProjection> summarizeReviewedByReviewerIds(
            @Param("reviewerIds") Collection<Long> reviewerIds,
            @Param("statuses") Collection<VideoStatus> statuses,
            @Param("approved") VideoStatus approved,
            @Param("rejected") VideoStatus rejected);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select v from Video v
            where v.reviewer.id = :reviewerId
              and v.status in :statuses
              and (:q = ''
                or lower(v.title) like lower(concat('%', :q, '%'))
                or lower(v.user.username) like lower(concat('%', :q, '%'))
                or lower(coalesce(v.rejectionReason, '')) like lower(concat('%', :q, '%')))
            order by v.reviewedAt desc, v.id desc
            """)
    Page<Video> searchReviewedByReviewer(@Param("reviewerId") Long reviewerId,
                                         @Param("statuses") Collection<VideoStatus> statuses,
                                         @Param("q") String q,
                                         Pageable pageable);
}
