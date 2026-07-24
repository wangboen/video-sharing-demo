package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    List<Video> findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus status);
    Optional<Video> findByIdAndStatus(Long id, VideoStatus status);
    List<Video> findAllByUserIdOrderByUploadDateDescIdDesc(Long userId);
    List<Video> findAllByStatusIsNull();

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
    List<Video> findClaimableForUpdate(@Param("status") VideoStatus status,
                                       @Param("now") Instant now,
                                       Pageable pageable);
}
