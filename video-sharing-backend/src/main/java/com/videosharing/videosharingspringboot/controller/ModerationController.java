package com.videosharing.videosharingspringboot.controller;

import com.videosharing.videosharingspringboot.config.ApiExceptionHandler;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionRequest;
import com.videosharing.videosharingspringboot.dto.ModerationDecisionResponse;
import com.videosharing.videosharingspringboot.dto.ModerationVideoResponse;
import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.service.ModerationService;
import com.videosharing.videosharingspringboot.service.StaffAccountService;
import jakarta.validation.Valid;
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

import java.util.List;

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
        return moderation.claimNext(authenticatedName(authentication))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/queue")
    public List<ModerationVideoResponse> queue(@RequestParam(defaultValue = "20") int limit,
                                               Authentication authentication) {
        return moderation.queue(authenticatedName(authentication), limit);
    }

    @PostMapping("/{id}/claim")
    public ModerationVideoResponse claim(@PathVariable Long id, Authentication authentication) {
        return moderation.claim(authenticatedName(authentication), id);
    }

    @PostMapping("/{id}/heartbeat")
    public ModerationVideoResponse heartbeat(@PathVariable Long id, Authentication authentication) {
        return moderation.heartbeat(id, authenticatedName(authentication));
    }

    @DeleteMapping("/{id}/claim")
    public ResponseEntity<Void> release(@PathVariable Long id,
                                        @RequestParam(defaultValue = "false") boolean force,
                                        Authentication authentication) {
        String username = authenticatedName(authentication);
        Role role = staffAccounts.findByUsername(username)
                .orElseThrow(() -> new ApiExceptionHandler.AuthenticationRequiredException("Staff account not found"))
                .getRole();
        moderation.release(id, username, force, role);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ModerationDecisionResponse decide(@PathVariable Long id,
                                             Authentication authentication,
                                             @Valid @RequestBody ModerationDecisionRequest request) {
        return moderation.decide(id, authenticatedName(authentication), request);
    }

    private static String authenticatedName(Authentication authentication) {
        if (authentication == null) {
            throw new ApiExceptionHandler.AuthenticationRequiredException("Authentication is required");
        }
        return authentication.getName();
    }
}
