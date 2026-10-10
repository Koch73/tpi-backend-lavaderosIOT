package ar.com.athiel.device;

public class UnknownDeviceException extends RuntimeException {
    public UnknownDeviceException(String id) {
        super("Device '" + id + "' not found");
    }
}
