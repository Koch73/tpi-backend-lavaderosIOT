package ar.com.athiel.common;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

/**
 * Issues and verifies the admin JWT (HS256, subject = username), same shape as
 * the Python {@code login.py}. Shared by auth-service (issue, /me) and the
 * api-gateway (verify).
 */
public class JwtTokenService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(120);

    private final SecretKey key;

    /** @param secret at least 32 bytes, required by HS256 */
    public JwtTokenService(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_KEY must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String username) {
        return issue(username, DEFAULT_TTL);
    }

    public String issue(String username, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** @return the username when the token is valid and not expired, otherwise empty */
    public Optional<String> verify(String token) {
        try {
            return Optional.ofNullable(
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Extracts the token from an {@code Authorization: Bearer ...} header value. */
    public static Optional<String> bearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        return Optional.of(authorizationHeader.substring(7).trim());
    }
}
