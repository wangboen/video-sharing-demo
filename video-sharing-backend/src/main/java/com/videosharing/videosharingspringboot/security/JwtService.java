package com.videosharing.videosharingspringboot.security;

import com.videosharing.videosharingspringboot.model.StaffAccount;
import com.videosharing.videosharingspringboot.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    public static final String ACCOUNT_TYPE_USER = "USER";
    public static final String ACCOUNT_TYPE_STAFF = "STAFF";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return generateToken(user.getUsername(), user.getId(), ACCOUNT_TYPE_USER);
    }

    public String generateStaffToken(StaffAccount staff) {
        return generateToken(staff.getUsername(), staff.getId(), ACCOUNT_TYPE_STAFF);
    }

    private String generateToken(String username, Long id, String accountType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .setId(UUID.randomUUID().toString())
                .claim("userId", id)
                .claim("accountType", accountType)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractSessionId(String token) {
        return extractClaims(token).getId();
    }

    public Long extractUserId(String token) {
        Number userId = extractClaims(token).get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    public String extractAccountType(String token) {
        String accountType = extractClaims(token).get("accountType", String.class);
        return accountType == null ? ACCOUNT_TYPE_USER : accountType;
    }

    public Duration getExpiration() {
        return Duration.ofMillis(expirationMs);
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject() != null && claims.getExpiration().after(new Date());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
