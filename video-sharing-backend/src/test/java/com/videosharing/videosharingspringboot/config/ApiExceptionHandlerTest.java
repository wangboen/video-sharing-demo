package com.videosharing.videosharingspringboot.config;

import com.videosharing.videosharingspringboot.messaging.CommentPublishException;
import com.videosharing.videosharingspringboot.service.ModerationConflictException;
import com.videosharing.videosharingspringboot.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsAuthenticationFailuresToJsonUnauthorizedResponse() {
        var response = handler.handleAuthentication(new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Authentication is required");
    }

    @Test
    void mapsAccessDeniedToJsonForbiddenResponse() {
        var response = handler.handleAccessDenied(new AccessDeniedException("ROLE_USER is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Access denied");
    }

    @Test
    void mapsMissingResourcesToJsonNotFoundResponse() {
        var response = handler.handleNotFound(new ApiExceptionHandler.ResourceNotFoundException("Comment not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Comment not found");
    }

    @Test
    void mapsConflictToJsonConflictResponse() {
        var response = handler.handleModerationConflict(new ModerationConflictException("Video is already leased"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Video is already leased");
    }

    @Test
    void mapsUnavailableDependenciesToJsonServiceUnavailableResponse() {
        assertThat(handler.handleServiceUnavailable(new CommentPublishException("RocketMQ unavailable")).getBody())
                .containsEntry("message", "Comment service is temporarily unavailable");
        assertThat(handler.handleServiceUnavailable(new DataAccessResourceFailureException("database down")).getBody())
                .containsEntry("message", "Service is temporarily unavailable");
        assertThat(handler.handleServiceUnavailable(new IOException("object storage failed")).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsObjectNotFoundToJsonNotFoundResponse() {
        var response = handler.handleObjectNotFound(new ObjectStorageService.ObjectNotFoundException("missing-key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Requested content was not found");
    }

    @Test
    void mapsUnexpectedErrorsToGenericJsonInternalServerError() {
        var response = handler.handleUnexpected(new RuntimeException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Service failed unexpectedly");
    }
}
