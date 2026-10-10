package ar.com.athiel.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WashSessionRepository extends JpaRepository<WashSession, String> {
    Optional<WashSession> findFirstByDeviceIdAndStatusOrderByCreatedAtDesc(String deviceId, SessionStatus status);

    List<WashSession> findByStatusAndExpiresAtLessThanEqual(SessionStatus status, Instant instant);

    List<WashSession> findByStatus(SessionStatus status);
}
