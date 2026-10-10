package ar.com.athiel.device;

/**
 * The only seam where a hardware vendor is visible. Adding a vendor means
 * adding one implementation and one {@link Vendor} value.
 */
public interface DeviceGateway {

    Vendor vendor();

    /** Sends the command. Throws {@link DeviceUnavailableException} on any communication failure. */
    void setPower(DeviceProperties.Device device, boolean on);

    /** Reads the relay's actual output state. */
    DeviceStatus readPower(DeviceProperties.Device device);
}
