package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentLike;
import com.videosharing.videosharingspringboot.model.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {
}
