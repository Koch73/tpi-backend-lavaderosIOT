package ar.com.athiel.device;

import ar.com.athiel.common.DeviceCommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal API, called only by order-service on the private network. The
 * gateway has no route to {@code /devices/**}.
 */
@RestController
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/devices/{deviceId}/on")
    public DeviceCommandResponse on(@PathVariable String deviceId,
                                    @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return devices.turnOn(deviceId, key);
    }

    @PostMapping("/devices/{deviceId}/off")
    public DeviceCommandResponse off(@PathVariable String deviceId,
                                     @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return devices.turnOff(deviceId, key);
    }

    @GetMapping("/devices/{deviceId}/status")
    public Map<String, String> status(@PathVariable String deviceId) {
        return Map.of("deviceId", deviceId, "state", devices.status(deviceId).name());
    }

    @ExceptionHandler(UnknownDeviceException.class)
    ResponseEntity<Map<String, String>> unknown(UnknownDeviceException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(DeviceUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(DeviceUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", e.getMessage()));
    }
}
