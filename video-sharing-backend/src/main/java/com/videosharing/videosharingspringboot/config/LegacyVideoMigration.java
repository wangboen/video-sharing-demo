package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import com.videosharing.videosharingspringboot.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class LegacyVideoMigration implements CommandLineRunner {
    private final VideoRepository videos;
    private final Path publicVideos;
    private final Path publicThumbnails;
    private final Path pendingVideos;
    private final Path pendingThumbnails;

    @Autowired
    public LegacyVideoMigration(VideoRepository videos,
                                @Value("${file.upload-dir}") String publicVideos,
                                @Value("${file.thumbnail-dir}") String publicThumbnails,
                                @Value("${file.pending-upload-dir}") String pendingVideos,
                                @Value("${file.pending-thumbnail-dir}") String pendingThumbnails) {
        this(videos, Paths.get(publicVideos), Paths.get(publicThumbnails),
                Paths.get(pendingVideos), Paths.get(pendingThumbnails));
    }

    LegacyVideoMigration(VideoRepository videos,
                         Path publicVideos,
                         Path publicThumbnails,
                         Path pendingVideos,
                         Path pendingThumbnails) {
        this.videos = videos;
        this.publicVideos = normalize(publicVideos);
        this.publicThumbnails = normalize(publicThumbnails);
        this.pendingVideos = normalize(pendingVideos);
        this.pendingThumbnails = normalize(pendingThumbnails);
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Video> legacy = videos.findAllByStatusIsNull();
        if (legacy.isEmpty()) {
            return;
        }

        List<FileState> states = new ArrayList<>();
        for (Video video : legacy) {
            states.add(validate(video.getFilePath(), publicVideos, pendingVideos));
            if (video.getThumbnailPath() != null) {
                states.add(validate(video.getThumbnailPath(), publicThumbnails, pendingThumbnails));
            }
        }

        List<Move> completed = new ArrayList<>();
        try {
            for (FileState state : states) {
                if (state.needsMove()) {
                    Files.createDirectories(state.destination().getParent());
                    Files.move(state.source(), state.destination());
                    completed.add(new Move(state.source(), state.destination()));
                }
            }
            legacy.forEach(video -> video.setStatus(VideoStatus.PENDING));
            videos.saveAll(legacy);
            restoreOnRollback(completed, legacy);
        } catch (IOException | RuntimeException exception) {
            restore(completed);
            legacy.forEach(video -> video.setStatus(null));
            throw new IllegalStateException("Legacy media migration failed", exception);
        }
    }

    private FileState validate(String fileName, Path sourceDirectory, Path destinationDirectory) {
        Path source = safeResolve(sourceDirectory, fileName);
        Path destination = safeResolve(destinationDirectory, fileName);
        boolean sourceExists = Files.isRegularFile(source);
        boolean destinationExists = Files.isRegularFile(destination);
        if (sourceExists == destinationExists) {
            throw new IllegalStateException(
                    "Unsafe legacy media state for " + fileName + ": exactly one source copy is required");
        }
        return new FileState(source, destination, sourceExists);
    }

    private void restoreOnRollback(List<Move> completed, List<Video> legacy) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        List<Move> snapshot = List.copyOf(completed);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    restore(snapshot);
                    legacy.forEach(video -> video.setStatus(null));
                }
            }
        });
    }

    private static void restore(List<Move> completed) {
        for (int index = completed.size() - 1; index >= 0; index--) {
            Move move = completed.get(index);
            try {
                Files.createDirectories(move.source().getParent());
                if (Files.exists(move.destination()) && !Files.exists(move.source())) {
                    Files.move(move.destination(), move.source());
                }
            } catch (IOException ignored) {
                // Startup still fails; the inconsistency is reported again on the next boot.
            }
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path safeResolve(Path directory, String fileName) {
        Path resolved = directory.resolve(fileName).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IllegalStateException("Invalid stored file name");
        }
        return resolved;
    }

    private record FileState(Path source, Path destination, boolean needsMove) {
    }

    private record Move(Path source, Path destination) {
    }
}
