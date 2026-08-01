package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {
    @Modifying
    @Query("delete from CommentLike l where l.id = :id")
    int deleteExisting(@Param("id") CommentLikeId id);
}
