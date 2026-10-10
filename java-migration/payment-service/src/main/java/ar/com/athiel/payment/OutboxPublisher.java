package ar.com.athiel.payment;

import ar.com.athiel.common.PaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends a {@link PaymentApprovedEvent} for every stored payment that has not
 * been published yet. If RabbitMQ is down the row stays unpublished and is
 * retried on the next tick, so an approved payment is never dropped.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final PaymentRecordRepository records;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(PaymentRecordRepository records, RabbitTemplate rabbit) {
        this.records = records;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${athiel.outbox.interval-ms:2000}")
    @Transactional
    public void publishPending() {
        for (PaymentRecord record : records.findByEventPublishedFalseOrderByIdAsc()) {
            try {
                rabbit.convertAndSend(
                        MessagingConfig.EXCHANGE,
                        MessagingConfig.ROUTING_KEY,
                        new PaymentApprovedEvent(record.getOrderId(), record.getPaymentId(), record.getAmount()));
                record.setEventPublished(true);
            } catch (Exception e) {
                log.error("Could not publish payment {}: {}", record.getPaymentId(), e.getMessage());
                return; // keep order, try again next tick
            }
        }
    }
}
