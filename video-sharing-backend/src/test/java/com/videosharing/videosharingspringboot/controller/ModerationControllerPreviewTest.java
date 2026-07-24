package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.service.ModerationPreview;
import com.videosharing.videosharingspringboot.service.ModerationService;
import com.videosharing.videosharingspringboot.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationControllerPreviewTest {
    @Test
    void responseUsesValidatedDescriptorHeadersAndNeverTheResourceExtension() throws Exception {
        ModerationService moderation = mock(ModerationService.class);
        Authentication authentication = mock(Authentication.class);
        ByteArrayResource attackerNamedResource = new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "attacker-controlled.html";
            }
        };
        MediaType videoMp4 = MediaType.parseMediaType("video/mp4");
        ModerationPreview preview = new ModerationPreview(
                attackerNamedResource, videoMp4, "preview.mp4");
        when(authentication.getName()).thenReturn("reviewer");
        when(moderation.preview(10L, "reviewer", false)).thenReturn(preview);
        ModerationController controller = new ModerationController(moderation, mock(UserService.class));

        var response = controller.previewVideo(10L, authentication);

        assertEquals(videoMp4, response.getHeaders().getContentType());
        assertEquals("inline; filename=\"preview.mp4\"",
                response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertSame(attackerNamedResource, response.getBody());
    }
}
