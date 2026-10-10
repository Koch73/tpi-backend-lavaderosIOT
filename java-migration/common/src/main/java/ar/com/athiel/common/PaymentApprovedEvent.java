package ar.com.athiel.common;

import java.math.BigDecimal;

/**
 * Published by payment-service once Mercado Pago has confirmed a payment as
 * approved. order-service consumes it to activate the washer.
 */
public record PaymentApprovedEvent(String orderId, String paymentId, BigDecimal amount) {
}
