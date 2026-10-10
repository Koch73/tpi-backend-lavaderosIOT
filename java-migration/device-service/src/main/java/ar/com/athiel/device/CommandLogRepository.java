package ar.com.athiel.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandLogRepository extends JpaRepository<CommandLog, Long> {
    Optional<CommandLog> findFirstByIdempotencyKeyAndCommandAndResultOrderByIdDesc(
            String idempotencyKey, String command, String result);
}
