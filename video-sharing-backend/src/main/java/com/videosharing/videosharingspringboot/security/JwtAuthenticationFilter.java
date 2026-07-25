package com.videosharing.videosharingspringboot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import com.videosharing.videosharingspringboot.model.Role;
import com.videosharing.videosharingspringboot.repository.StaffAccountRepository;
import com.videosharing.videosharingspringboot.repository.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;
    private final UserRepository userRepository;
    private final StaffAccountRepository staffAccountRepository;

    public JwtAuthenticationFilter(JwtService jwtService, RedisSessionService redisSessionService,
                                   UserRepository userRepository,
                                   StaffAccountRepository staffAccountRepository) {
        this.jwtService = jwtService;
        this.redisSessionService = redisSessionService;
        this.userRepository = userRepository;
        this.staffAccountRepository = staffAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorization.substring(7);
            if (jwtService.isTokenValid(token)) {
                String username = jwtService.extractUsername(token);
                String sessionId = jwtService.extractSessionId(token);
                Long userId = jwtService.extractUserId(token);
                String accountType = jwtService.extractAccountType(token);
                if (sessionId != null && userId != null && redisSessionService.isSessionActive(sessionId, userId)) {
                    if (JwtService.ACCOUNT_TYPE_STAFF.equals(accountType)) {
                        staffAccountRepository.findById(userId)
                                .filter(staff -> username.equals(staff.getUsername()))
                                .ifPresent(staff -> authenticate(request, username, staff.getRole()));
                    } else {
                        userRepository.findById(userId)
                                .filter(user -> username.equals(user.getUsername()))
                                .ifPresent(user -> authenticate(request, username, user.getRole()));
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String username, Role role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
