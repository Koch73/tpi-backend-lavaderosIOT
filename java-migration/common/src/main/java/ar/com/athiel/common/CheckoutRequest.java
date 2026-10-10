package ar.com.athiel.common;

import java.math.BigDecimal;

/** order-service -> payment-service: create a Mercado Pago checkout for an order. */
public record CheckoutRequest(String orderId, String title, BigDecimal amount) {
}
