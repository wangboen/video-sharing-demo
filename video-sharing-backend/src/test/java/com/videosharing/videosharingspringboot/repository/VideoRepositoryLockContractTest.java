package com.videosharing.videosharingspringboot.repository;

import com.videosharing.videosharingspringboot.model.VideoStatus;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VideoRepositoryLockContractTest {

    @Test
    void queueReadQueryDoesNotRequestWriteLocks() throws NoSuchMethodException {
        var method = VideoRepository.class.getMethod(
                "findClaimable", VideoStatus.class, Instant.class, Pageable.class);

        assertThat(method.getAnnotation(Lock.class)).isNull();
    }

    @Test
    void claimQueryRequestsWriteLocks() throws NoSuchMethodException {
        var method = VideoRepository.class.getMethod(
                "findClaimableForUpdate", VideoStatus.class, Instant.class, Pageable.class);

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
