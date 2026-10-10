package ar.com.athiel.gateway;

import ar.com.athiel.common.JwtTokenService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rejects calls to protected routes that do not carry a valid admin JWT.
 * Which routes are protected is configuration, see
 * {@code athiel.gateway.protected-routes} in application.yml.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private record Rule(HttpMethod method, String pattern) {
    }

    private final JwtTokenService jwt;
    private final List<Rule> rules;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthFilter(GatewayAuthProperties props) {
        this.jwt = new JwtTokenService(props.jwtSecret());
        this.rules = props.protectedRoutes().stream().map(entry -> {
            String[] parts = entry.trim().split("\\s+", 2);
            return new Rule(HttpMethod.valueOf(parts[0].toUpperCase()), parts[1]);
        }).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();

        boolean needsAuth = rules.stream()
                .anyMatch(r -> r.method().equals(method) && matcher.match(r.pattern(), path));
        if (!needsAuth) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean valid = JwtTokenService.bearer(header).flatMap(jwt::verify).isPresent();
        if (valid) {
            return chain.filter(exchange);
        }
        return unauthorized(exchange);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"detail\":\"Token inválido\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
