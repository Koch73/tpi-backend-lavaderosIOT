package ar.com.athiel.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentServiceTest {

    private MercadoPagoClient mp;
    private PaymentRecordRepository repo;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        mp = mock(MercadoPagoClient.class);
        repo = mock(PaymentRecordRepository.class);
        var props = new MercadoPagoProperties("t", "http://x", "", "http://n", "http://b", Duration.ofMinutes(5));
        service = new PaymentService(mp, repo, props);
    }

    private PaymentInfo payment(String status, String ref, OffsetDateTime created) {
        return new PaymentInfo("1", status, ref, new BigDecimal("3500"), created);
    }

    @Test
    void approvedRecentPaymentIsAccepted() {
        when(mp.getPayment("1")).thenReturn(payment("approved", "order-1", OffsetDateTime.now().minusMinutes(1)));
        assertEquals(PaymentService.Outcome.ACCEPTED, service.handleNotification("1"));
        verify(repo).saveAndFlush(any(PaymentRecord.class));
    }

    @Test
    void alreadyProcessedPaymentDoesNotCallMercadoPago() {
        when(repo.existsByPaymentId("1")).thenReturn(true);
        assertEquals(PaymentService.Outcome.DUPLICATE, service.handleNotification("1"));
        verify(mp, never()).getPayment(any());
    }

    @Test
    void rejectedPaymentIsNotRecorded() {
        when(mp.getPayment("1")).thenReturn(payment("rejected", "order-1", OffsetDateTime.now()));
        assertEquals(PaymentService.Outcome.NOT_APPROVED, service.handleNotification("1"));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void oldPaymentIsRejected() {
        when(mp.getPayment("1")).thenReturn(payment("approved", "order-1", OffsetDateTime.now().minusMinutes(30)));
        assertEquals(PaymentService.Outcome.TOO_OLD, service.handleNotification("1"));
    }

    @Test
    void paymentWithoutOrderReferenceIsRejected() {
        when(mp.getPayment("1")).thenReturn(payment("approved", null, OffsetDateTime.now()));
        assertEquals(PaymentService.Outcome.NO_ORDER_REFERENCE, service.handleNotification("1"));
    }

    @Test
    void concurrentWebhookLosingTheRaceIsDuplicate() {
        when(mp.getPayment("1")).thenReturn(payment("approved", "order-1", OffsetDateTime.now()));
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertEquals(PaymentService.Outcome.DUPLICATE, service.handleNotification("1"));
    }
}
