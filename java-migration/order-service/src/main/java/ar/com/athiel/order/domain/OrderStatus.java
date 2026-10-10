package ar.com.athiel.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    /** Paid amount differs from the order amount; nothing was started, needs a human. */
    AMOUNT_MISMATCH,
    CANCELLED
}
