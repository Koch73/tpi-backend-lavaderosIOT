package ar.com.athiel.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shelly Cloud control API, as used by {@code cloud_providers/shelly/shelly_api.py}.
 *
 * @param baseUrl  server URL (HOST_SHELLY)
 * @param authKey  cloud authorization key (SHELLY_TOKEN)
 */
@ConfigurationProperties(prefix = "athiel.shelly")
public record ShellyProperties(String baseUrl, String authKey) {
}
