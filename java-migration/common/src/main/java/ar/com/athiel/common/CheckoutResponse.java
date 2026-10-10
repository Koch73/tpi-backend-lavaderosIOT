package ar.com.athiel.common;

/** payment-service -> order-service: URL the customer is sent to in order to pay. */
public record CheckoutResponse(String checkoutUrl) {
}
