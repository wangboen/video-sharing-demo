package com.videosharing.videosharingspringboot.security;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final RedisSessionService redisSessionService = mock(RedisSessionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StaffAccountRepository staffAccountRepository = mock(StaffAccountRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, redisSessionService,
            userRepository, staffAccountRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateWhenTheRedisSessionIsMissing() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        mockValidJwt();
        when(redisSessionService.isSessionActive("session-1", 42L)).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticatesOnlyWhenTheRedisSessionMatchesTheJwtUser() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        mockValidJwt();
        when(redisSessionService.isSessionActive("session-1", 42L)).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user(Role.USER)));
        when(jwtService.extractAccountType("signed-jwt")).thenReturn(JwtService.ACCOUNT_TYPE_USER);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertEquals("creator", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void loadsRoleAuthorityFromCurrentStaffAccountWhenJwtIsAStaffToken() throws Exception {
        MockHttpServletRequest request = bearerRequest();
        mockValidJwt();
        when(redisSessionService.isSessionActive("session-1", 42L)).thenReturn(true);
        when(jwtService.extractAccountType("signed-jwt")).thenReturn(JwtService.ACCOUNT_TYPE_STAFF);
        when(staffAccountRepository.findById(42L)).thenReturn(java.util.Optional.of(staff(Role.ADMIN)));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed-jwt");
        return request;
    }

    private void mockValidJwt() {
        when(jwtService.isTokenValid("signed-jwt")).thenReturn(true);
        when(jwtService.extractUsername("signed-jwt")).thenReturn("creator");
        when(jwtService.extractSessionId("signed-jwt")).thenReturn("session-1");
        when(jwtService.extractUserId("signed-jwt")).thenReturn(42L);
    }

    private User user(Role role) {
        User user = new User();
        user.setId(42L);
        user.setUsername("creator");
        user.setRole(role);
        return user;
    }

    private StaffAccount staff(Role role) {
        StaffAccount staff = new StaffAccount();
        staff.setId(42L);
        staff.setUsername("creator");
        staff.setRole(role);
        return staff;
    }
}
