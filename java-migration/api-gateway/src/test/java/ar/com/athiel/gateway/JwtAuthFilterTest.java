package ar.com.athiel.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.athiel.common.JwtTokenService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtAuthFilter filter = new JwtAuthFilter(new GatewayAuthProperties(SECRET,
            List.of("GET /settings", "POST /boxes/**", "GET /reports/**")));

    private boolean passes(MockServerHttpRequest.BaseBuilder<?> request, MockServerWebExchange[] out) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        out[0] = exchange;
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(exchange, e -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();
        return forwarded.get();
    }

    @Test
    void publicRouteIsForwardedWithoutToken() {
        var out = new MockServerWebExchange[1];
        assertTrue(passes(MockServerHttpRequest.post("/webhook"), out));
        assertTrue(passes(MockServerHttpRequest.get("/boxes/time_left"), out));
    }

    @Test
    void protectedRouteWithoutTokenIs401() {
        var out = new MockServerWebExchange[1];
        assertFalse(passes(MockServerHttpRequest.get("/settings"), out));
        assertEquals(HttpStatus.UNAUTHORIZED, out[0].getResponse().getStatusCode());
        assertFalse(passes(MockServerHttpRequest.post("/boxes/box1"), out));
    }

    @Test
    void protectedRouteWithValidTokenIsForwarded() {
        String token = new JwtTokenService(SECRET).issue("admin");
        var out = new MockServerWebExchange[1];
        assertTrue(passes(MockServerHttpRequest.get("/reports/monthly").header("Authorization", "Bearer " + token), out));
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String token = new JwtTokenService("ffffffffffffffffffffffffffffffff").issue("admin");
        var out = new MockServerWebExchange[1];
        assertFalse(passes(MockServerHttpRequest.get("/settings").header("Authorization", "Bearer " + token), out));
    }
}
