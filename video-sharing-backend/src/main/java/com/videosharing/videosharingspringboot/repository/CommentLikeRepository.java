package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {
    @Modifying
    @Query(value = """
            insert into comment_likes (comment_id, user_id, created_at)
            select :commentId, :userId, :createdAt
            where not exists (
                select 1 from comment_likes
                where comment_id = :commentId
                  and user_id = :userId
            )
            """, nativeQuery = true)
    int insertIfAbsent(@Param("commentId") Long commentId,
                       @Param("userId") Long userId,
                       @Param("createdAt") Instant createdAt);

    @Modifying
    @Query("delete from CommentLike l where l.id = :id")
    int deleteExisting(@Param("id") CommentLikeId id);
}
