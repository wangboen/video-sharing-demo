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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus status);
    Page<Video> findAllByStatusOrderByViewsDescUploadDateDescIdDesc(VideoStatus status, Pageable pageable);
    @Query("""
            select v.id from Video v
            where v.status = :status
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    List<Long> findHotCandidateIds(@Param("status") VideoStatus status, Pageable pageable);

    @Query("""
            select v.id from Video v
            where v.status = :status
              and v.category.id = :categoryId
              and v.category.enabled = true
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    List<Long> findHotCandidateIdsByCategory(@Param("status") VideoStatus status,
                                             @Param("categoryId") Long categoryId,
                                             Pageable pageable);

    @Query("""
            select v.id from Video v
            where v.status = :status
              and v.uploadDate >= :uploadedAfter
            order by v.uploadDate desc, v.id desc
            """)
    List<Long> findFreshCandidateIds(@Param("status") VideoStatus status,
                                     @Param("uploadedAfter") LocalDateTime uploadedAfter,
                                     Pageable pageable);

    @Query("""
            select v.id from Video v
            where v.status = :status
              and v.category.id = :categoryId
              and v.category.enabled = true
              and v.uploadDate >= :uploadedAfter
            order by v.uploadDate desc, v.id desc
            """)
    List<Long> findFreshCandidateIdsByCategory(@Param("status") VideoStatus status,
                                               @Param("categoryId") Long categoryId,
                                               @Param("uploadedAfter") LocalDateTime uploadedAfter,
                                               Pageable pageable);

    @Query("""
            select v.id from Video v
            where v.status = :status
              and v.uploadDate >= :uploadedAfter
              and v.views <= :maxViews
            order by v.uploadDate desc, v.id desc
            """)
    List<Long> findExploreCandidateIds(@Param("status") VideoStatus status,
                                       @Param("uploadedAfter") LocalDateTime uploadedAfter,
                                       @Param("maxViews") long maxViews,
                                       Pageable pageable);

    @Query("""
            select v.id from Video v
            where v.status = :status
              and v.category.id = :categoryId
              and v.category.enabled = true
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    List<Long> findCategoryCandidateIds(@Param("status") VideoStatus status,
                                        @Param("categoryId") Long categoryId,
                                        Pageable pageable);

    @Query("""
            select distinct v.id from Video v
            join v.tags tag
            where v.status = :status
              and tag.id = :tagId
              and tag.enabled = true
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    List<Long> findTagCandidateIds(@Param("status") VideoStatus status,
                                   @Param("tagId") Long tagId,
                                   Pageable pageable);

    List<Video> findAllByIdIn(Collection<Long> ids);

    @Query("""
            select v from Video v
            where v.status = :status
              and v.category.id = :categoryId
              and v.category.enabled = true
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    Page<Video> findRecommendedByCategory(@Param("status") VideoStatus status,
                                          @Param("categoryId") Long categoryId,
                                          Pageable pageable);
    @Query("""
            select distinct v from Video v
            join v.tags tag
            where v.status = :status
              and tag.id = :tagId
              and tag.enabled = true
            order by v.views desc, v.uploadDate desc, v.id desc
            """)
    Page<Video> findRecommendedByTag(@Param("status") VideoStatus status,
                                     @Param("tagId") Long tagId,
                                     Pageable pageable);
    Optional<Video> findByIdAndStatus(Long id, VideoStatus status);
    Page<Video> findAllByUserIdOrderByUploadDateDescIdDesc(Long userId, Pageable pageable);
    Optional<Video> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select v from Video v
            where v.status = :status
              and (lower(v.title) like lower(concat('%', :query, '%'))
                or lower(coalesce(v.description, '')) like lower(concat('%', :query, '%')))
            order by v.uploadDate desc, v.id desc
            """)
    Page<Video> searchPublicVideos(@Param("status") VideoStatus status,
                                   @Param("query") String query,
                                   Pageable pageable);

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
