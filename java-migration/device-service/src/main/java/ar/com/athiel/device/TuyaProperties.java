package ar.com.athiel.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl      Tuya OpenAPI endpoint for the data center (TUYA_BASE_URL)
 * @param clientId     access id
 * @param clientSecret access secret
 */
@ConfigurationProperties(prefix = "athiel.tuya")
public record TuyaProperties(String baseUrl, String clientId, String clientSecret) {
}
