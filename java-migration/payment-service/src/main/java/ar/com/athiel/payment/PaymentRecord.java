package ar.com.athiel.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per approved payment. Replaces {@code processed_payments}: the
 * UNIQUE constraint on payment_id is what makes duplicate webhooks harmless.
 * {@code eventPublished} makes the table double as an outbox.
 */
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 50)
    private String paymentId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "event_published", nullable = false)
    private boolean eventPublished;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PaymentRecord() {
    }

    public PaymentRecord(String paymentId, String orderId, BigDecimal amount, String status) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public boolean isEventPublished() { return eventPublished; }
    public void setEventPublished(boolean eventPublished) { this.eventPublished = eventPublished; }
}
