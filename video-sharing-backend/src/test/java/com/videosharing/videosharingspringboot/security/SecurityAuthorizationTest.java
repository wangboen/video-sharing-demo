package com.videosharing.videosharingspringboot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.FilterChain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityAuthorizationTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityAuthorizationTest.ProbeController.class})
class SecurityAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void onlyUserCanUpload() throws Exception {
        mvc.perform(post("/api/videos/upload-session").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/videos/upload-session").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/videos/upload-complete").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/videos/upload-complete").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/videos/upload-cancel").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/videos/upload-cancel").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/videos/10/download/video").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/videos/10/download/video").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/videos/10/download/thumbnail").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/videos/10/download/thumbnail").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/comments").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/comments").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/me/comments/10").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/me/comments/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanManageReviewers() throws Exception {
        mvc.perform(get("/api/admin/reviewers").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reviewers").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reviewers/9/reviews").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reviewers/9/reviews").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void reviewerAndAdminCanModerateButUserCannot() throws Exception {
        mvc.perform(post("/api/moderation/videos/claim-next").with(user("creator").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/moderation/videos/claim-next").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/moderation/videos/claim-next").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/moderation/videos/queue").with(user("creator").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/moderation/videos/queue").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/moderation/videos/10/claim").with(user("creator").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/moderation/videos/10/claim").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {
        @PostMapping("/api/videos/upload-session")
        ResponseEntity<Void> uploadSession() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/videos/upload-complete")
        ResponseEntity<Void> uploadComplete() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/videos/upload-cancel")
        ResponseEntity<Void> uploadCancel() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/users/me/videos/{id}/download/video")
        ResponseEntity<Void> downloadVideo() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/users/me/videos/{id}/download/thumbnail")
        ResponseEntity<Void> downloadThumbnail() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/users/me/comments")
        ResponseEntity<Void> comments() {
            return ResponseEntity.ok().build();
        }

        @DeleteMapping("/api/users/me/comments/{id}")
        ResponseEntity<Void> comment(@PathVariable Long id) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/admin/reviewers")
        ResponseEntity<Void> reviewers() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/admin/reviewers/{id}/reviews")
        ResponseEntity<Void> reviewerReviews() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/moderation/videos/claim-next")
        ResponseEntity<Void> moderate() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/moderation/videos/queue")
        ResponseEntity<Void> queue() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/moderation/videos/{id}/claim")
        ResponseEntity<Void> claim(@PathVariable Long id) {
            return ResponseEntity.ok().build();
        }
    }
}
