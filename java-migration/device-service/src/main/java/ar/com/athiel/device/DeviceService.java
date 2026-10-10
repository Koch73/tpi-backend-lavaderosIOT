package ar.com.athiel.device;

import ar.com.athiel.common.DeviceCommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs a command against a logical device with bounded retries, reads the
 * state back to confirm it, logs every attempt and honours the idempotency key.
 *
 * "Command sent" and "state confirmed" are different things (Tuya reported
 * success for a relay that was offline), so the response carries
 * {@code confirmed} separately.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceProperties props;
    private final Map<Vendor, DeviceGateway> gateways;
    private final CommandLogRepository commandLog;

    public DeviceService(DeviceProperties props, List<DeviceGateway> gateways, CommandLogRepository commandLog) {
        this.props = props;
        this.gateways = gateways.stream().collect(Collectors.toMap(DeviceGateway::vendor, Function.identity()));
        this.commandLog = commandLog;
    }

    public DeviceCommandResponse turnOn(String deviceId, String idempotencyKey) {
        return execute(deviceId, idempotencyKey, true);
    }

    public DeviceCommandResponse turnOff(String deviceId, String idempotencyKey) {
        return execute(deviceId, idempotencyKey, false);
    }

    public DeviceStatus status(String deviceId) {
        DeviceProperties.Device device = props.require(deviceId);
        return gateway(device).readPower(device);
    }

    private DeviceCommandResponse execute(String deviceId, String key, boolean on) {
        DeviceProperties.Device device = props.require(deviceId);
        String command = on ? "ON" : "OFF";
        DeviceStatus expected = on ? DeviceStatus.ON : DeviceStatus.OFF;

        if (key != null && commandLog
                .findFirstByIdempotencyKeyAndCommandAndResultOrderByIdDesc(key, command, "CONFIRMED").isPresent()) {
            log.info("Command {} {} with key {} already confirmed, not re-sending", command, deviceId, key);
            return new DeviceCommandResponse(deviceId, expected.name(), true);
        }

        DeviceGateway gateway = gateway(device);
        DeviceUnavailableException last = null;

        for (int attempt = 1; attempt <= Math.max(1, props.attempts()); attempt++) {
            try {
                gateway.setPower(device, on);
                sleep(props.confirmDelay().toMillis());
                DeviceStatus actual = gateway.readPower(device);

                boolean confirmed = actual == expected;
                commandLog.save(new CommandLog(deviceId, key, command, confirmed ? "CONFIRMED" : "UNCONFIRMED",
                        confirmed ? null : "read back " + actual));
                if (confirmed) {
                    return new DeviceCommandResponse(deviceId, actual.name(), true);
                }
                log.warn("Device {} accepted {} but reports {} (attempt {})", deviceId, command, actual, attempt);
                last = new DeviceUnavailableException("State not confirmed: " + actual);
            } catch (DeviceUnavailableException e) {
                last = e;
                commandLog.save(new CommandLog(deviceId, key, command, "FAILED", e.getMessage()));
                log.warn("Device {} command {} failed (attempt {}): {}", deviceId, command, attempt, e.getMessage());
            }
            if (attempt < props.attempts()) {
                sleep(props.retryDelay().toMillis());
            }
        }
        if (last != null && last.getMessage() != null && last.getMessage().startsWith("State not confirmed")) {
            return new DeviceCommandResponse(deviceId, DeviceStatus.UNKNOWN.name(), false);
        }
        throw last != null ? last : new DeviceUnavailableException("Command failed");
    }

    private DeviceGateway gateway(DeviceProperties.Device device) {
        DeviceGateway gateway = gateways.get(device.vendor());
        if (gateway == null) {
            throw new IllegalStateException("No gateway for vendor " + device.vendor());
        }
        return gateway;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
