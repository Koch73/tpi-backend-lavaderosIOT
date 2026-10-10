package ar.com.athiel.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A period during which the washer is on. {@code expiresAt} lives in the
 * database, so a restart does not lose the timer (it was a daemon thread plus
 * relay_state.json in Python). Paying again while RUNNING extends the same
 * session instead of creating another one, like {@code Relay.start} did.
 */
@Entity
@Table(name = "wash_sessions", indexes = @Index(columnList = "status,expires_at"))
public class WashSession {

    @Id
    @Column(length = 64)
    private String id = UUID.randomUUID().toString();

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.ACTIVATING;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WashSession() {
    }

    public WashSession(String deviceId, int durationSeconds, BigDecimal totalAmount, String paymentMethod) {
        this.deviceId = deviceId;
        this.durationSeconds = durationSeconds;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
    }

    public String getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String reason) {
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }
}
