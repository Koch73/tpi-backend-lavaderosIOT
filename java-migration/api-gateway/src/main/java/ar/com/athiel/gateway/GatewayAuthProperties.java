package ar.com.athiel.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param jwtSecret  shared HS256 secret (same value as auth-service)
 * @param protectedRoutes entries "METHOD /ant/path"; a request matching one needs a valid Bearer token
 */
@ConfigurationProperties(prefix = "athiel.gateway")
public record GatewayAuthProperties(String jwtSecret, List<String> protectedRoutes) {
}
