package com.videosharing.videosharingspringboot.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "comment_likes")
@Data
public class CommentLike {
    @EmbeddedId
    private CommentLikeId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
