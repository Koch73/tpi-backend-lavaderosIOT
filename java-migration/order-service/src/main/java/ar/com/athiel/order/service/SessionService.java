package ar.com.athiel.order.service;

import ar.com.athiel.common.DeviceCommandResponse;
import ar.com.athiel.order.OrderProperties;
import ar.com.athiel.order.client.DeviceClient;
import ar.com.athiel.order.domain.Order;
import ar.com.athiel.order.domain.OrderRepository;
import ar.com.athiel.order.domain.RelayError;
import ar.com.athiel.order.domain.RelayErrorRepository;
import ar.com.athiel.order.domain.SessionStatus;
import ar.com.athiel.order.domain.WashHistory;
import ar.com.athiel.order.domain.WashHistoryRepository;
import ar.com.athiel.order.domain.WashSession;
import ar.com.athiel.order.domain.WashSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the wash session lifecycle, replacing {@code Relay}, {@code RelayControl}
 * and relay_state.json. All state is in the database.
 *
 * <pre>
 * ACTIVATING -> RUNNING -> STOPPING -> COMPLETED
 *     |                        |
 *     v                        v
 * ACTIVATION_FAILED      SHUTDOWN_FAILED (retried by the timer)
 * </pre>
 *
 * Device commands are never sent inside a database transaction. A per-device
 * lock serialises activations and shutdowns within this instance.
 */
@Service
public class SessionService {

    public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final WashSessionRepository sessions;
    private final OrderRepository orders;
    private final WashHistoryRepository history;
    private final RelayErrorRepository errors;
    private final DeviceClient devices;
    private final ServiceCatalog catalog;
    private final TransactionTemplate tx;
    private final Duration shutdownRetry;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public SessionService(WashSessionRepository sessions, OrderRepository orders, WashHistoryRepository history,
                          RelayErrorRepository errors, DeviceClient devices, ServiceCatalog catalog,
                          TransactionTemplate tx, OrderProperties props) {
        this.sessions = sessions;
        this.orders = orders;
        this.history = history;
        this.errors = errors;
        this.devices = devices;
        this.catalog = catalog;
        this.tx = tx;
        this.shutdownRetry = props.shutdownRetry();
    }

    // ---------------------------------------------------------------- activation

    /**
     * Starts the washer for a paid order, or extends the running session.
     *
     * @throws ActivationFailedException when the device could not be started; the
     *         session is then persisted as ACTIVATION_FAILED
     */
    public void activate(String orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order " + orderId));
        String deviceId = catalog.require(order.getServiceCode()).deviceId();

        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            WashSession running = sessions
                    .findFirstByDeviceIdAndStatusOrderByCreatedAtDesc(deviceId, SessionStatus.RUNNING).orElse(null);
            if (running != null) {
                extend(running, order);
                return;
            }

            WashSession session = tx.execute(status -> {
                WashSession s = new WashSession(deviceId, order.getDurationSeconds(), order.getAmount(),
                        order.getPaymentMethod());
                sessions.save(s);
                order.setSessionId(s.getId());
                orders.save(order);
                return s;
            });

            DeviceCommandResponse response;
            try {
                response = devices.on(deviceId, session.getId());
            } catch (RuntimeException e) {
                failActivation(session, "turn_on", e.getMessage());
                throw new ActivationFailedException(e.getMessage());
            }
            if (!response.confirmed()) {
                failActivation(session, "turn_on", "device did not confirm ON (state " + response.state() + ")");
                throw new ActivationFailedException("el equipo no confirmó el encendido");
            }

            Instant now = Instant.now();
            session.setStatus(SessionStatus.RUNNING);
            session.setStartedAt(now);
            session.setExpiresAt(now.plusSeconds(session.getDurationSeconds()));
            sessions.save(session);
            log.info("Session {} RUNNING on {} until {}", session.getId(), deviceId, session.getExpiresAt());
        } finally {
            lock.unlock();
        }
    }

    private void extend(WashSession running, Order order) {
        tx.executeWithoutResult(status -> {
            running.setExpiresAt(running.getExpiresAt().plusSeconds(order.getDurationSeconds()));
            running.setDurationSeconds(running.getDurationSeconds() + order.getDurationSeconds());
            running.setTotalAmount(running.getTotalAmount().add(order.getAmount()));
            sessions.save(running);
            order.setSessionId(running.getId());
            orders.save(order);
        });
        log.info("Session {} extended by {}s, now expires {}", running.getId(), order.getDurationSeconds(),
                running.getExpiresAt());
    }

    private void failActivation(WashSession session, String operation, String message) {
        session.setStatus(SessionStatus.ACTIVATION_FAILED);
        session.setFailureReason(message);
        session.setFinishedAt(Instant.now());
        sessions.save(session);
        errors.save(new RelayError(session.getDeviceId(), operation, message));
        log.error("Session {} ACTIVATION_FAILED: {}", session.getId(), message);
    }

    // ------------------------------------------------------------------ shutdown

    /** Called every second by {@link SessionTimerJob}: stops expired sessions and retries failed shutdowns. */
    public void expireDue() {
        Instant now = Instant.now();
        for (WashSession s : sessions.findByStatusAndExpiresAtLessThanEqual(SessionStatus.RUNNING, now)) {
            stop(s);
        }
        for (WashSession s : sessions.findByStatus(SessionStatus.SHUTDOWN_FAILED)) {
            Instant last = s.getLastAttemptAt();
            if (last == null || Duration.between(last, now).compareTo(shutdownRetry) >= 0) {
                stop(s);
            }
        }
    }

    /** Admin action behind {@code POST /boxes/{box}?action=stop}. */
    public void stopNow(String deviceId) {
        WashSession running = sessions
                .findFirstByDeviceIdAndStatusOrderByCreatedAtDesc(deviceId, SessionStatus.RUNNING).orElse(null);
        if (running != null) {
            stop(running);
            return;
        }
        // nothing tracked as running: still make sure the relay is off, like the Python endpoint did
        devices.off(deviceId, null);
    }

    void stop(WashSession session) {
        ReentrantLock lock = lockFor(session.getDeviceId());
        lock.lock();
        try {
            session.setStatus(SessionStatus.STOPPING);
            session.setLastAttemptAt(Instant.now());
            sessions.save(session);

            boolean confirmed;
            String reason = null;
            try {
                // new key per attempt: a previous failed OFF must be re-sent
                DeviceCommandResponse r = devices.off(session.getDeviceId(),
                        session.getId() + ":off:" + System.nanoTime());
                confirmed = r.confirmed();
                if (!confirmed) {
                    reason = "device did not confirm OFF (state " + r.state() + ")";
                }
            } catch (RuntimeException e) {
                confirmed = false;
                reason = e.getMessage();
            }

            if (confirmed) {
                complete(session);
            } else {
                session.setStatus(SessionStatus.SHUTDOWN_FAILED);
                session.setFailureReason(reason);
                sessions.save(session);
                errors.save(new RelayError(session.getDeviceId(), "turn_off", reason));
                log.error("Session {} SHUTDOWN_FAILED: {}", session.getId(), reason);
            }
        } finally {
            lock.unlock();
        }
    }

    private void complete(WashSession session) {
        tx.executeWithoutResult(status -> {
            session.setStatus(SessionStatus.COMPLETED);
            session.setFailureReason(null);
            session.setFinishedAt(Instant.now());
            sessions.save(session);
            history.save(new WashHistory(
                    session.getDeviceId(),
                    "stopped",
                    session.getDurationSeconds(),
                    session.getTotalAmount(),
                    LocalDate.now(ARGENTINA).toString(),
                    session.getPaymentMethod(),
                    session.getId()));
        });
        log.info("Session {} COMPLETED", session.getId());
    }

    // ------------------------------------------------------------------ recovery

    /**
     * After a restart: RUNNING sessions need nothing (the timer reads
     * {@code expires_at}); interrupted ones are resolved so no relay is left
     * on without an owner.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        for (WashSession s : sessions.findByStatus(SessionStatus.ACTIVATING)) {
            try {
                devices.off(s.getDeviceId(), null);
            } catch (RuntimeException e) {
                errors.save(new RelayError(s.getDeviceId(), "reconcile_off", e.getMessage()));
            }
            failActivation(s, "reconcile", "interrupted by restart while activating");
        }
        for (WashSession s : sessions.findByStatus(SessionStatus.STOPPING)) {
            s.setStatus(SessionStatus.SHUTDOWN_FAILED);
            s.setLastAttemptAt(null);
            s.setFailureReason("interrupted by restart while stopping");
            sessions.save(s);
        }
        List<WashSession> running = sessions.findByStatus(SessionStatus.RUNNING);
        log.info("Reconciled on startup: {} running session(s) will be stopped by the timer when due",
                running.size());
    }

    // ------------------------------------------------------------------- queries

    /** Seconds left for the running session of a device; 0 when none. No side effects. */
    public double secondsLeft(String deviceId) {
        return sessions.findFirstByDeviceIdAndStatusOrderByCreatedAtDesc(deviceId, SessionStatus.RUNNING)
                .map(s -> Math.max(0, Duration.between(Instant.now(), s.getExpiresAt()).toMillis() / 1000.0))
                .map(v -> Math.round(v * 100.0) / 100.0)
                .orElse(0.0);
    }

    public boolean isRunning(String deviceId) {
        return sessions.findFirstByDeviceIdAndStatusOrderByCreatedAtDesc(deviceId, SessionStatus.RUNNING).isPresent();
    }

    private ReentrantLock lockFor(String deviceId) {
        return locks.computeIfAbsent(deviceId, k -> new ReentrantLock());
    }
}
