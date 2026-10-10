package ar.com.athiel.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuedTokenVerifiesToSameUser() {
        JwtTokenService jwt = new JwtTokenService(SECRET);
        assertEquals("admin", jwt.verify(jwt.issue("admin")).orElseThrow());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenService jwt = new JwtTokenService(SECRET);
        assertTrue(jwt.verify(jwt.issue("admin", Duration.ofSeconds(-5))).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtTokenService("ffffffffffffffffffffffffffffffff").issue("admin");
        assertTrue(new JwtTokenService(SECRET).verify(token).isEmpty());
    }

    @Test
    void shortSecretIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("short"));
    }

    @Test
    void bearerHeaderIsParsed() {
        assertEquals("abc", JwtTokenService.bearer("Bearer abc").orElseThrow());
        assertTrue(JwtTokenService.bearer("Basic abc").isEmpty());
        assertTrue(JwtTokenService.bearer(null).isEmpty());
    }
}
