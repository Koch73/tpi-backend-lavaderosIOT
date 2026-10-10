package ar.com.athiel.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WashHistoryRepository extends JpaRepository<WashHistory, Long> {
    List<WashHistory> findByWashDateBetweenOrderByWashDateAscCreatedAtAsc(String from, String to);
}
