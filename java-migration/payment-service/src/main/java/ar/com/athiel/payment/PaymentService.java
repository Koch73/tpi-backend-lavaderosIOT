package ar.com.athiel.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Port of {@code process_payment_notification}: look the payment up with
 * Mercado Pago, accept only approved and recent payments, record it once.
 * Starting the washer is no longer done here; an event is published instead.
 */
@Service
public class PaymentService {

    public enum Outcome {
        ACCEPTED, DUPLICATE, NOT_APPROVED, TOO_OLD, NO_ORDER_REFERENCE
    }

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final MercadoPagoClient mercadoPago;
    private final PaymentRecordRepository records;
    private final Duration approvalWindow;

    public PaymentService(MercadoPagoClient mercadoPago, PaymentRecordRepository records,
                          MercadoPagoProperties props) {
        this.mercadoPago = mercadoPago;
        this.records = records;
        this.approvalWindow = props.approvalWindow();
    }

    public Outcome handleNotification(String paymentId) {
        if (records.existsByPaymentId(paymentId)) {
            log.info("Payment {} already processed", paymentId);
            return Outcome.DUPLICATE;
        }

        // Always ask Mercado Pago for the real status; never trust the webhook body.
        PaymentInfo payment = mercadoPago.getPayment(paymentId);

        if (!"approved".equals(payment.status())) {
            log.warn("Payment {} has status '{}', not activating", paymentId, payment.status());
            return Outcome.NOT_APPROVED;
        }

        OffsetDateTime created = payment.dateCreated();
        if (created == null || Duration.between(created, OffsetDateTime.now()).compareTo(approvalWindow) > 0) {
            log.warn("Payment {} approved outside the {} window (created {})", paymentId, approvalWindow, created);
            return Outcome.TOO_OLD;
        }

        if (payment.externalReference() == null || payment.externalReference().isBlank()) {
            log.warn("Payment {} has no external_reference, cannot match an order", paymentId);
            return Outcome.NO_ORDER_REFERENCE;
        }

        try {
            records.saveAndFlush(new PaymentRecord(
                    paymentId, payment.externalReference(), payment.transactionAmount(), payment.status()));
        } catch (DataIntegrityViolationException e) {
            // concurrent webhook won the race on the UNIQUE(payment_id) constraint
            log.info("Payment {} recorded concurrently by another webhook", paymentId);
            return Outcome.DUPLICATE;
        }
        return Outcome.ACCEPTED;
    }
}
