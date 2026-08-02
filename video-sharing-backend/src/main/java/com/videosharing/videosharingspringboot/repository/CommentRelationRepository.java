package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CommentRelationRepository extends JpaRepository<CommentRelation, Long> {

    @Query("""
            select c from CommentRelation c
            where c.videoId = :videoId
              and c.status = com.videosharing.videosharingspringboot.model.CommentStatus.VISIBLE
              and c.likeCount >= :minLikes
            order by c.likeCount desc, c.createdAt desc, c.commentId desc
            """)
    List<CommentRelation> findTopHotVisible(@Param("videoId") Long videoId,
                                            @Param("minLikes") long minLikes,
                                            Pageable pageable);

    @Query("""
            select c from CommentRelation c
            where c.videoId = :videoId
              and c.status = com.videosharing.videosharingspringboot.model.CommentStatus.VISIBLE
            order by c.createdAt desc, c.commentId desc
            """)
    List<CommentRelation> findVisibleByVideo(@Param("videoId") Long videoId,
                                             Pageable pageable);

    @Query("""
            select c from CommentRelation c
            where c.videoId = :videoId
              and c.status = com.videosharing.videosharingspringboot.model.CommentStatus.VISIBLE
              and (c.createdAt < :beforeCreatedAt
                or (c.createdAt = :beforeCreatedAt and c.commentId < :beforeCommentId))
            order by c.createdAt desc, c.commentId desc
            """)
    List<CommentRelation> findVisibleByVideoAfterCursor(@Param("videoId") Long videoId,
                                                         @Param("beforeCreatedAt") Instant beforeCreatedAt,
                                                         @Param("beforeCommentId") Long beforeCommentId,
                                                         Pageable pageable);

    Page<CommentRelation> findByUserIdAndAnonymizedFalse(Long userId, Pageable pageable);

    @Modifying
    @Query("""
            update CommentRelation c
            set c.likeCount = c.likeCount + 1
            where c.commentId = :commentId
              and c.status = com.videosharing.videosharingspringboot.model.CommentStatus.VISIBLE
            """)
    int incrementVisibleLikeCount(@Param("commentId") Long commentId);

    @Modifying
    @Query("""
            update CommentRelation c
            set c.likeCount = case when c.likeCount > 0 then c.likeCount - 1 else 0 end
            where c.commentId = :commentId
              and c.status = com.videosharing.videosharingspringboot.model.CommentStatus.VISIBLE
            """)
    int decrementVisibleLikeCount(@Param("commentId") Long commentId);
}
