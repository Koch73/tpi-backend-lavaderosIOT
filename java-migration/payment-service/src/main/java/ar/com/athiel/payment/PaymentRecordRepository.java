package ar.com.athiel.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    boolean existsByPaymentId(String paymentId);

    List<PaymentRecord> findByEventPublishedFalseOrderByIdAsc();
}
