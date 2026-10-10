package ar.com.athiel.order.service;

public class ActivationFailedException extends RuntimeException {
    public ActivationFailedException(String message) {
        super(message);
    }
}
