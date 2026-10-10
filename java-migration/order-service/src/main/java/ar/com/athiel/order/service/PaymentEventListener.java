package ar.com.athiel.order.service;

import ar.com.athiel.common.PaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orders;

    public PaymentEventListener(OrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = "order-service.payment-approved")
    public void onPaymentApproved(PaymentApprovedEvent event) {
        try {
            orders.onPaymentApproved(event);
        } catch (Exception e) {
            // The failure is already persisted (session ACTIVATION_FAILED or relay_errors).
            // Re-throwing would make the broker redeliver forever.
            log.error("Could not handle payment {}: {}", event.paymentId(), e.getMessage());
        }
    }
}
