package ar.com.athiel.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** The fields of a Mercado Pago payment this service cares about. */
public record PaymentInfo(
        String id,
        String status,
        String externalReference,
        BigDecimal transactionAmount,
        OffsetDateTime dateCreated) {
}
