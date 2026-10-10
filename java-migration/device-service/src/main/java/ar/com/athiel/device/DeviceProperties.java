package ar.com.athiel.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Logical devices ("box1") and how to reach them. The list replaces the
 * hard-coded {@code DEVICE_ID} and {@code "box1"} constants of the Python code.
 *
 * @param attempts      tries per command before giving up
 * @param retryDelay    pause between tries
 * @param confirmDelay  wait before reading the state back after a command
 */
@ConfigurationProperties(prefix = "athiel.devices")
public record DeviceProperties(
        List<Device> list,
        int attempts,
        Duration retryDelay,
        Duration confirmDelay) {

    public record Device(String id, Vendor vendor, String externalId, int channel) {
    }

    public Device require(String id) {
        return list.stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new UnknownDeviceException(id));
    }
}
