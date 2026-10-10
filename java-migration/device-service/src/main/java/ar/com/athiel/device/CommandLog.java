package ar.com.athiel.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per command attempt. Plays the role of {@code relay_errors} but
 * records successes too, and carries the idempotency key so a retried request
 * from order-service cannot switch the relay twice.
 */
@Entity
@Table(name = "command_log", indexes = @Index(columnList = "idempotency_key,command"))
public class CommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    private String deviceId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 10)
    private String command;

    /** CONFIRMED, UNCONFIRMED or FAILED */
    @Column(nullable = false, length = 15)
    private String result;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CommandLog() {
    }

    public CommandLog(String deviceId, String idempotencyKey, String command, String result, String error) {
        this.deviceId = deviceId;
        this.idempotencyKey = idempotencyKey;
        this.command = command;
        this.result = result;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public String getResult() { return result; }
    public String getDeviceId() { return deviceId; }
}
