package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionResponse;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.service.ModerationService;
import com.videosharing.videosharingspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/moderation/videos")
public class ModerationController {
    private final ModerationService moderation;
    private final UserService users;

    public ModerationController(ModerationService moderation, UserService users) {
        this.moderation = moderation;
        this.users = users;
    }

    @PostMapping("/claim-next")
    public ResponseEntity<ModerationVideoResponse> claimNext(Authentication authentication) {
        return moderation.claimNext(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/heartbeat")
    public ModerationVideoResponse heartbeat(@PathVariable Long id, Authentication authentication) {
        return moderation.heartbeat(id, authentication.getName());
    }

    @DeleteMapping("/{id}/claim")
    public ResponseEntity<Void> release(@PathVariable Long id,
                                        @RequestParam(defaultValue = "false") boolean force,
                                        Authentication authentication) {
        Role role = users.findByUsername(authentication.getName())
                .orElseThrow()
                .getRole();
        moderation.release(id, authentication.getName(), force, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview/video")
    public ResponseEntity<Resource> previewVideo(@PathVariable Long id, Authentication authentication)
            throws IOException {
        return preview(moderation.preview(id, authentication.getName(), false));
    }

    @GetMapping("/{id}/preview/thumbnail")
    public ResponseEntity<Resource> previewThumbnail(@PathVariable Long id, Authentication authentication)
            throws IOException {
        return preview(moderation.preview(id, authentication.getName(), true));
    }

    @PatchMapping("/{id}")
    public ModerationDecisionResponse decide(@PathVariable Long id,
                                             Authentication authentication,
                                             @Valid @RequestBody ModerationDecisionRequest request) {
        return moderation.decide(id, authentication.getName(), request);
    }

    private ResponseEntity<Resource> preview(Resource resource) throws IOException {
        String type = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String detected = java.nio.file.Files.probeContentType(resource.getFile().toPath());
        if (detected != null) {
            type = detected;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename().replace("\"", "") + "\"")
                .body(resource);
    }
}
