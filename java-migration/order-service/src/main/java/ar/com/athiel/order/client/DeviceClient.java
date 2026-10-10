package ar.com.athiel.order.client;

import ar.com.athiel.common.DeviceCommandResponse;

/** Port to device-service. Throws {@link DeviceCallException} when it cannot be reached or refuses. */
public interface DeviceClient {

    DeviceCommandResponse on(String deviceId, String idempotencyKey);

    DeviceCommandResponse off(String deviceId, String idempotencyKey);

    /** @return ON, OFF or UNKNOWN */
    String status(String deviceId);
}
