package ar.com.athiel.common;

/**
 * device-service -> order-service.
 *
 * @param deviceId  logical device (e.g. "box1")
 * @param state     last state read back from the device: ON, OFF or UNKNOWN
 * @param confirmed true only when the read-back state matches the command that was sent
 */
public record DeviceCommandResponse(String deviceId, String state, boolean confirmed) {
}
