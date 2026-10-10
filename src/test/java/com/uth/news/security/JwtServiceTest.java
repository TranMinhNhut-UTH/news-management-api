package com.uth.news.security;

import io.jsonwebtoken.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "test-only-secret-0123456789-abcdef";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JwtService service = new JwtService(SECRET, 3600000, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTripAndConfiguredLifetime() {
        String token = service.generateToken("writer");
        assertThat(service.extractUsername(token)).isEqualTo("writer");
        Claims claims = Jwts.parser().verifyWith(key(SECRET)).clock(() -> Date.from(NOW))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(3600000);
        assertThat(service.getExpiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredTokenIsRejectedWithoutSleeping() {
        String token = service.generateToken("writer");
        JwtService later = new JwtService(SECRET, 3600000,
                Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void wrongSignatureIsRejected() {
        String token = new JwtService("other-test-secret-0123456789-abcdef", 3600000,
                Clock.fixed(NOW, ZoneOffset.UTC)).generateToken("writer");
        assertThatThrownBy(() -> service.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void malformedAndUnsignedTokensAreRejected() {
        assertThatThrownBy(() -> service.extractUsername("not-a-jwt")).isInstanceOf(JwtException.class);
        String unsigned = Jwts.builder().subject("writer").compact();
        assertThatThrownBy(() -> service.extractUsername(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void missingExpirationAndSubjectAreRejected() {
        String missingExpiration = Jwts.builder().subject("writer").issuedAt(Date.from(NOW))
                .signWith(key(SECRET), Jwts.SIG.HS256).compact();
        String missingSubject = Jwts.builder().issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600))).signWith(key(SECRET), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service.extractUsername(missingExpiration)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.extractUsername(missingSubject)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingWeakSecretAndInvalidLifetime() {
        assertThatThrownBy(() -> new JwtService(null, 3600000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService("short", 3600000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(" ".repeat(32), 3600000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretLengthUsesUtf8BytesAndLifetimeIsConfigurable() {
        JwtService custom = new JwtService("é".repeat(16), 120000);
        assertThat(custom.extractUsername(custom.generateToken("writer"))).isEqualTo("writer");
        assertThat(custom.getExpiresInSeconds()).isEqualTo(120);
    }

    private SecretKeySpec key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
