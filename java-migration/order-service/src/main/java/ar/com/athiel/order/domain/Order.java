package ar.com.athiel.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the customer asked for. Price and duration are frozen here when the
 * order is created, so a later settings change cannot alter a paid order.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 64)
    private String id = UUID.randomUUID().toString();

    @Version
    private Long version;

    @Column(name = "service_code", nullable = false, length = 20)
    private String serviceCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "payment_id", length = 50)
    private String paymentId;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Order() {
    }

    public Order(String serviceCode, BigDecimal amount, int durationSeconds) {
        this.serviceCode = serviceCode;
        this.amount = amount;
        this.durationSeconds = durationSeconds;
    }

    public String getId() { return id; }
    public String getServiceCode() { return serviceCode; }
    public BigDecimal getAmount() { return amount; }
    public int getDurationSeconds() { return durationSeconds; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getPaymentId() { return paymentId; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public void markPaid(String paymentId, String paymentMethod) {
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }
}
