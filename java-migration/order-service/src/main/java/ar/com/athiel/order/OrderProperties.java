package ar.com.athiel.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param washDeviceId     logical device the WASH service switches (the "box1" constant in Python)
 * @param deviceServiceUrl base URL of device-service
 * @param paymentServiceUrl base URL of payment-service
 * @param shutdownRetry    minimum pause between attempts to power a session off after a failure
 */
@ConfigurationProperties(prefix = "athiel")
public record OrderProperties(
        String washDeviceId,
        String deviceServiceUrl,
        String paymentServiceUrl,
        Duration shutdownRetry) {
}
