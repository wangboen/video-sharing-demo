package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionResponse;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.service.ModerationPreview;
import com.videosharing.videosharingspringboot.service.ModerationService;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/api/moderation/videos")
public class ModerationController {
    private final ModerationService moderation;
    private final StaffAccountService staffAccounts;

    public ModerationController(ModerationService moderation, StaffAccountService staffAccounts) {
        this.moderation = moderation;
        this.staffAccounts = staffAccounts;
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
        Role role = staffAccounts.findByUsername(authentication.getName())
                .orElseThrow()
                .getRole();
        moderation.release(id, authentication.getName(), force, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/preview/video")
    public ResponseEntity<Resource> previewVideo(@PathVariable Long id, Authentication authentication) {
        return preview(moderation.preview(id, authentication.getName(), false));
    }

    @GetMapping("/{id}/preview/thumbnail")
    public ResponseEntity<Resource> previewThumbnail(@PathVariable Long id, Authentication authentication) {
        return preview(moderation.preview(id, authentication.getName(), true));
    }

    @PatchMapping("/{id}")
    public ModerationDecisionResponse decide(@PathVariable Long id,
                                             Authentication authentication,
                                             @Valid @RequestBody ModerationDecisionRequest request) {
        return moderation.decide(id, authentication.getName(), request);
    }

    private ResponseEntity<Resource> preview(ModerationPreview preview) {
        return ResponseEntity.ok()
                .contentType(preview.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(preview.safeFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(preview.resource());
    }
}
