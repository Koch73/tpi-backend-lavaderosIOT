package ar.com.athiel.order.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Replaces the daemon thread in {@code RelayControl._monitor_relay}. It holds
 * no state: every tick reads {@code expires_at} from the database, so it
 * recovers by itself after a restart.
 */
@Component
@ConditionalOnProperty(name = "athiel.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SessionTimerJob {

    private final SessionService sessions;

    public SessionTimerJob(SessionService sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "${athiel.timer.interval-ms:1000}")
    public void tick() {
        sessions.expireDue();
    }
}
