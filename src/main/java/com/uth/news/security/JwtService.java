package com.uth.news.security;

import io.jsonwebtoken.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this(secret, expirationMs, Clock.systemUTC());
    }

    JwtService(String secret, long expirationMs, Clock clock) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (expirationMs < 1000) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS must be at least 1000");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public String generateToken(String username) {
        Date issuedAt = Date.from(clock.instant());
        return Jwts.builder().subject(username).issuedAt(issuedAt)
                .expiration(new Date(Math.addExact(issuedAt.getTime(), expirationMs)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public String extractUsername(String token) {
        Jws<Claims> jwt = Jwts.parser().verifyWith(key)
                .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token);
        Claims claims = jwt.getPayload();
        if (!"HS256".equals(jwt.getHeader().getAlgorithm())
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getExpiration() == null || claims.getIssuedAt() == null) {
            throw new MalformedJwtException("Invalid access token claims");
        }
        return claims.getSubject();
    }

    public long getExpiresInSeconds() {
        return expirationMs / 1000;
    }
}
