package ar.com.athiel.order.client;

import java.math.BigDecimal;

/** Port to payment-service. */
public interface PaymentClient {

    /** @return the Mercado Pago URL the customer must open to pay */
    String createCheckout(String orderId, String title, BigDecimal amount);
}
