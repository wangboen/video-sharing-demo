package com.videosharing.videosharingspringboot.service;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RejectedVideoCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RejectedVideoCleanupService.class);
    private static final int BATCH_SIZE = 100;

    private final VideoRepository videos;
    private final ObjectStorageService objectStorage;
    private final Duration rejectedDownloadRetention;
    private final Clock clock;

    @Autowired
    public RejectedVideoCleanupService(VideoRepository videos,
                                       ObjectStorageService objectStorage,
                                       @Value("${video-download.rejected-retention-days:7}") long rejectedRetentionDays) {
        this(videos, objectStorage, Duration.ofDays(rejectedRetentionDays), Clock.systemUTC());
    }

    RejectedVideoCleanupService(VideoRepository videos,
                                ObjectStorageService objectStorage,
                                Duration rejectedDownloadRetention,
                                Clock clock) {
        this.videos = videos;
        this.objectStorage = objectStorage;
        this.rejectedDownloadRetention = rejectedDownloadRetention;
        this.clock = clock;
    }

    @Scheduled(cron = "${video-download.rejected-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredRejectedFiles() {
        Instant now = clock.instant();
        Instant expiredBefore = now.minus(rejectedDownloadRetention);
        List<Video> expired = videos.findRejectedWithDownloadableObjectsBefore(
                VideoStatus.REJECTED, expiredBefore, PageRequest.of(0, BATCH_SIZE));
        for (Video video : expired) {
            boolean changed = false;
            if (video.getFilePath() != null && video.getFileDeletedAt() == null) {
                changed = deleteObject(video.getId(), "video", video.getFilePath(), now);
                if (changed) {
                    video.setFileDeletedAt(now);
                }
            }
            if (video.getThumbnailPath() != null && video.getThumbnailDeletedAt() == null) {
                boolean thumbnailDeleted = deleteObject(video.getId(), "thumbnail", video.getThumbnailPath(), now);
                if (thumbnailDeleted) {
                    video.setThumbnailDeletedAt(now);
                    changed = true;
                }
            }
            if (changed) {
                videos.save(video);
            }
        }
    }

    private boolean deleteObject(Long videoId, String kind, String key, Instant now) {
        try {
            objectStorage.delete(key);
            return true;
        } catch (ObjectStorageService.ObjectNotFoundException exception) {
            return true;
        } catch (RuntimeException exception) {
            log.warn("Could not delete expired rejected {} object for video {} at {}: {}",
                    kind, videoId, now, exception.getMessage());
            return false;
        }
    }
}
