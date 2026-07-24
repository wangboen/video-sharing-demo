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
import com.videosharing.videosharingspringboot.repository.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, RedisSessionService redisSessionService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.redisSessionService = redisSessionService;
        this.userRepository = userRepository;
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
                if (sessionId != null && userId != null && redisSessionService.isSessionActive(sessionId, userId)) {
                    userRepository.findById(userId)
                            .filter(user -> username.equals(user.getUsername()))
                            .ifPresent(user -> {
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(username, null,
                                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
