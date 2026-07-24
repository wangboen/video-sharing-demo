package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyVideoMigrationTest {
    @TempDir Path temp;
    private final VideoRepository videos = mock(VideoRepository.class);

    @Test
    void movesLegacyPublicMediaToPendingAndMarksPending() throws Exception {
        Video video = legacy("video.mp4", "thumb.png");
        when(videos.findAllByStatusIsNull()).thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("public-videos"));
        Files.createDirectories(temp.resolve("public-thumbnails"));
        Files.writeString(temp.resolve("public-videos/video.mp4"), "video");
        Files.writeString(temp.resolve("public-thumbnails/thumb.png"), "thumb");

        migration().run();

        assertEquals(VideoStatus.PENDING, video.getStatus());
        assertTrue(Files.exists(temp.resolve("pending-videos/video.mp4")));
        assertTrue(Files.exists(temp.resolve("pending-thumbnails/thumb.png")));
        verify(videos).saveAll(List.of(video));
    }

    @Test
    void alreadyMigratedMediaMakesRerunIdempotent() throws Exception {
        Video video = legacy("video.mp4", null);
        when(videos.findAllByStatusIsNull()).thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");

        migration().run();

        assertEquals(VideoStatus.PENDING, video.getStatus());
    }

    @Test
    void bothPublicAndPendingCopiesFailStartup() throws Exception {
        Video video = legacy("video.mp4", null);
        when(videos.findAllByStatusIsNull()).thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("public-videos"));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("public-videos/video.mp4"), "video");
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");

        assertThrows(IllegalStateException.class, migration()::run);
        assertNull(video.getStatus());
    }

    @Test
    void missingLegacyMediaFailsStartup() {
        Video video = legacy("video.mp4", null);
        when(videos.findAllByStatusIsNull()).thenReturn(List.of(video));
        assertThrows(IllegalStateException.class, migration()::run);
    }

    @Test
    void restartAuditMovesPendingMediaFoundOnlyInLegacyPublicStorage() throws Exception {
        Video video = pending("video.mp4", "thumb.png");
        when(videos.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.PENDING))
                .thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("public-videos"));
        Files.createDirectories(temp.resolve("public-thumbnails"));
        Files.writeString(temp.resolve("public-videos/video.mp4"), "video");
        Files.writeString(temp.resolve("public-thumbnails/thumb.png"), "thumb");

        migration().run();

        assertTrue(Files.exists(temp.resolve("pending-videos/video.mp4")));
        assertTrue(Files.exists(temp.resolve("pending-thumbnails/thumb.png")));
        assertFalse(Files.exists(temp.resolve("public-videos/video.mp4")));
        assertFalse(Files.exists(temp.resolve("public-thumbnails/thumb.png")));
        verify(videos, never()).saveAll(any());
    }

    @Test
    void restartAuditAcceptsPendingMediaOnlyInPrivateStorage() throws Exception {
        Video video = pending("video.mp4", null);
        when(videos.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.PENDING))
                .thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");

        migration().run();

        assertTrue(Files.exists(temp.resolve("pending-videos/video.mp4")));
    }

    @Test
    void restartAuditFailsWhenPendingMediaExistsInBothPublicAndPrivateStorage() throws Exception {
        Video video = pending("video.mp4", null);
        when(videos.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.PENDING))
                .thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("public-videos"));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("public-videos/video.mp4"), "video");
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");

        assertThrows(IllegalStateException.class, migration()::run);
    }

    @Test
    void restartAuditFailsWhenPendingThumbnailIsMissing() throws Exception {
        Video video = pending("video.mp4", "thumb.png");
        when(videos.findAllByStatusOrderByUploadDateAscIdAsc(VideoStatus.PENDING))
                .thenReturn(List.of(video));
        Files.createDirectories(temp.resolve("pending-videos"));
        Files.writeString(temp.resolve("pending-videos/video.mp4"), "video");

        assertThrows(IllegalStateException.class, migration()::run);
    }

    private LegacyVideoMigration migration() {
        return new LegacyVideoMigration(videos,
                temp.resolve("public-videos"), temp.resolve("public-thumbnails"),
                temp.resolve("pending-videos"), temp.resolve("pending-thumbnails"));
    }

    private Video legacy(String file, String thumbnail) {
        Video video = new Video();
        video.setStatus(null);
        video.setFilePath(file);
        video.setThumbnailPath(thumbnail);
        return video;
    }

    private Video pending(String file, String thumbnail) {
        Video video = legacy(file, thumbnail);
        video.setStatus(VideoStatus.PENDING);
        return video;
    }
}
