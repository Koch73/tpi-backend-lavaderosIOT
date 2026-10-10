package ar.com.athiel.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Same columns as the Python {@code relay_errors} table. */
@Entity
@Table(name = "relay_errors")
public class RelayError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relay_name", nullable = false, length = 50)
    private String relayName;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RelayError() {
    }

    public RelayError(String relayName, String operation, String message) {
        this.relayName = relayName;
        this.operation = operation;
        this.message = message == null ? "" : message.substring(0, Math.min(message.length(), 500));
    }

    public String getRelayName() { return relayName; }
    public String getOperation() { return operation; }
    public String getMessage() { return message; }
}
