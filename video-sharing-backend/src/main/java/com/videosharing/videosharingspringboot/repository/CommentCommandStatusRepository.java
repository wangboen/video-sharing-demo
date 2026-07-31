package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.CommentCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentCommandStatusRepository extends JpaRepository<CommentCommandStatus, String> {
}
