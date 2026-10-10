package ar.com.athiel.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Same columns as the Python {@code wash_history} table; one row per completed session. */
@Entity
@Table(name = "wash_history", indexes = @Index(columnList = "wash_date"))
public class WashHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relay_name", nullable = false, length = 50)
    private String relayName;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "duration_seconds", nullable = false)
    private double durationSeconds;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "wash_date", nullable = false, length = 10)
    private String washDate;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WashHistory() {
    }

    public WashHistory(String relayName, String state, double durationSeconds, BigDecimal price,
                       String washDate, String paymentMethod, String sessionId) {
        this.relayName = relayName;
        this.state = state;
        this.durationSeconds = durationSeconds;
        this.price = price;
        this.washDate = washDate;
        this.paymentMethod = paymentMethod;
        this.sessionId = sessionId;
    }

    public Long getId() { return id; }
    public String getRelayName() { return relayName; }
    public BigDecimal getPrice() { return price; }
    public String getWashDate() { return washDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public Instant getCreatedAt() { return createdAt; }
}
