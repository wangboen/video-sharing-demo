package com.videosharing.videosharingspringboot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CommentIdJsonSerializationTest {
    private static final long UNSAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_993L;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesAcceptedCommentIdAsStringToPreserveFrontendPrecision() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new CreateCommentAcceptedResponse("request-1", UNSAFE_JAVASCRIPT_INTEGER, "ACCEPTED")));

        assertThat(json.get("commentId").isTextual()).isTrue();
        assertThat(json.get("commentId").asText()).isEqualTo("9007199254740993");
    }

    @Test
    void serializesListedCommentIdAsStringToPreserveFrontendPrecision() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new CommentResponse(UNSAFE_JAVASCRIPT_INTEGER, "alice", "hello", "text/plain",
                        0L, false, false, Instant.parse("2026-08-03T00:00:00Z"))));

        assertThat(json.get("commentId").isTextual()).isTrue();
        assertThat(json.get("commentId").asText()).isEqualTo("9007199254740993");
    }

    @Test
    void serializesCommandStatusCommentIdAsStringToPreserveFrontendPrecision() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new CommentCommandStatusResponse("request-1", UNSAFE_JAVASCRIPT_INTEGER, "ACCEPTED", null)));

        assertThat(json.get("commentId").isTextual()).isTrue();
        assertThat(json.get("commentId").asText()).isEqualTo("9007199254740993");
    }
}
