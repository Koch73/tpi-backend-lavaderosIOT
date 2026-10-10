package ar.com.athiel.order.service;

import ar.com.athiel.common.PaymentApprovedEvent;
import ar.com.athiel.order.client.PaymentClient;
import ar.com.athiel.order.domain.Order;
import ar.com.athiel.order.domain.OrderRepository;
import ar.com.athiel.order.domain.OrderStatus;
import ar.com.athiel.order.domain.RelayError;
import ar.com.athiel.order.domain.RelayErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Commercial side: creates orders from the server-side catalog and starts the
 * wash only for an order whose payment was verified and matches its amount.
 */
@Service
public class OrderService {

    public record Checkout(String orderId, String checkoutUrl) {
    }

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final RelayErrorRepository errors;
    private final ServiceCatalog catalog;
    private final PaymentClient payments;
    private final SessionService sessions;

    public OrderService(OrderRepository orders, RelayErrorRepository errors, ServiceCatalog catalog,
                        PaymentClient payments, SessionService sessions) {
        this.orders = orders;
        this.errors = errors;
        this.catalog = catalog;
        this.payments = payments;
        this.sessions = sessions;
    }

    /** POST /create_preference: price and duration come from the catalog. */
    public Checkout createOrder(String serviceCode) {
        CatalogItem item = catalog.require(serviceCode);
        Order order = orders.save(new Order(item.code(), item.price(), item.durationSeconds()));
        try {
            String url = payments.createCheckout(order.getId(), item.title(), item.price());
            return new Checkout(order.getId(), url);
        } catch (RuntimeException e) {
            order.setStatus(OrderStatus.CANCELLED);
            orders.save(order);
            errors.save(new RelayError(item.deviceId(), "create_preference", e.getMessage()));
            throw e;
        }
    }

    /** Consumes a PaymentApprovedEvent. Safe to receive more than once. */
    public void onPaymentApproved(PaymentApprovedEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) {
            errors.save(new RelayError("box1", "payment_event",
                    "Pago " + event.paymentId() + " para orden desconocida " + event.orderId()));
            log.warn("Payment {} references unknown order {}", event.paymentId(), event.orderId());
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("Order {} is {}, ignoring repeated payment event {}", order.getId(), order.getStatus(),
                    event.paymentId());
            return;
        }
        if (event.amount() == null || event.amount().compareTo(order.getAmount()) != 0) {
            order.setStatus(OrderStatus.AMOUNT_MISMATCH);
            orders.save(order);
            errors.save(new RelayError("box1", "payment_amount",
                    "Pago " + event.paymentId() + " por " + event.amount() + " no coincide con la orden "
                            + order.getId() + " (" + order.getAmount() + ")"));
            return;
        }

        order.markPaid(event.paymentId(), "mercadopago");
        orders.save(order); // @Version makes a concurrent duplicate fail here
        sessions.activate(order.getId());
    }

    /** POST /payments/manual: cash or transfer taken by an admin. */
    public void registerManualPayment() {
        CatalogItem item = catalog.require(ServiceCatalog.WASH);
        Order order = new Order(item.code(), item.price(), item.durationSeconds());
        order.markPaid(null, "manual");
        orders.save(order);
        sessions.activate(order.getId());
    }
}
