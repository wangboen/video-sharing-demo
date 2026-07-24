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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.FilterChain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mvc.perform(post("/api/videos/upload").with(user("creator").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/videos/upload").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanManageReviewers() throws Exception {
        mvc.perform(get("/api/admin/reviewers").with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reviewers").with(user("admin").roles("ADMIN")))
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
    }

    @RestController
    static class ProbeController {
        @PostMapping("/api/videos/upload")
        ResponseEntity<Void> upload() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/admin/reviewers")
        ResponseEntity<Void> reviewers() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/moderation/videos/claim-next")
        ResponseEntity<Void> moderate() {
            return ResponseEntity.ok().build();
        }
    }
}
