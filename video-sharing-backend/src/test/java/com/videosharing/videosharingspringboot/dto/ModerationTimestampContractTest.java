package com.videosharing.videosharingspringboot.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.videosharing.videosharingspringboot.model.Video;
import com.videosharing.videosharingspringboot.model.VideoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationTimestampContractTest {
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json()
            .modulesToInstall(JavaTimeModule.class)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void claimedLeaseExpiryAndReviewedTimestampsUseInstantAcrossPersistenceAndApiTypes() throws Exception {
        assertEquals(Instant.class, Video.class.getDeclaredField("claimedAt").getType());
        assertEquals(Instant.class, Video.class.getDeclaredField("leaseExpiresAt").getType());
        assertEquals(Instant.class, Video.class.getDeclaredField("reviewedAt").getType());
        assertEquals(Instant.class, ModerationVideoResponse.class.getMethod("claimedAt").getReturnType());
        assertEquals(Instant.class, ModerationVideoResponse.class.getMethod("leaseExpiresAt").getReturnType());
        assertEquals(Instant.class, ModerationDecisionResponse.class.getMethod("reviewedAt").getReturnType());
        assertEquals(Instant.class, UserSubmissionResponse.class.getMethod("reviewedAt").getReturnType());
        assertEquals(LocalDateTime.class, ModerationVideoResponse.class.getMethod("uploadDate").getReturnType());
    }

    @Test
    void moderationJsonCarriesAnExplicitUtcOffset() throws Exception {
        Object response = moderationResponse();

        String serialized = json.writeValueAsString(response);

        assertTrue(serialized.contains("\"claimedAt\":\"2026-07-24T00:00:00Z\""), serialized);
        assertTrue(serialized.contains("\"leaseExpiresAt\":\"2026-07-24T00:15:00Z\""), serialized);
    }

    private Object moderationResponse() throws Exception {
        Constructor<?> constructor = ModerationVideoResponse.class.getDeclaredConstructors()[0];
        Class<?> claimedType = constructor.getParameterTypes()[7];
        Object claimed = timestamp(claimedType, "2026-07-24T00:00:00Z");
        Object expires = timestamp(claimedType, "2026-07-24T00:15:00Z");
        return constructor.newInstance(42L, "Title", "Description", "creator",
                LocalDateTime.of(2026, 7, 23, 10, 0), VideoStatus.PENDING, 4L,
                claimed, expires, true);
    }

    private Object timestamp(Class<?> type, String value) {
        Instant instant = Instant.parse(value);
        if (type == Instant.class) {
            return instant;
        }
        return LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }
}
