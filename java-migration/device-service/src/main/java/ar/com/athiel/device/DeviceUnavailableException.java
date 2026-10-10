package ar.com.athiel.device;

/** The device (or its vendor cloud) could not be reached or refused the command. */
public class DeviceUnavailableException extends RuntimeException {
    public DeviceUnavailableException(String message) {
        super(message);
    }

    public DeviceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
