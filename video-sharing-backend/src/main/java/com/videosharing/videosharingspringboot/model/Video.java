package com.videosharing.videosharingspringboot.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "videos")
@Data
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String filePath;

    private String thumbnailPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private VideoCategory category;

    @ManyToMany
    @JoinTable(
            name = "video_tag_relation",
            joinColumns = @JoinColumn(name = "video_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<VideoTag> tags = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "upload_date", nullable = false, updatable = false)
    private LocalDateTime uploadDate;

    private Long views = 0L;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VideoStatus status = VideoStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private StaffAccount reviewer;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "file_deleted_at")
    private Instant fileDeletedAt;

    @Column(name = "thumbnail_deleted_at")
    private Instant thumbnailDeletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lease_owner_id")
    private StaffAccount leaseOwner;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
}
