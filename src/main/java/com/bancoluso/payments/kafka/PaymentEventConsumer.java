package com.bancoluso.payments.kafka;

import com.bancoluso.payments.config.KafkaConfig;
import com.bancoluso.payments.dto.PaymentRequest;
import com.bancoluso.payments.entity.Payment;
import com.bancoluso.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentRepository paymentRepository;

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(PaymentRequest event) {

        Instant eventTs = event.getEventTimestamp() != null
                ? event.getEventTimestamp()
                : Instant.now();

        log.info("Received payment event: referenceId={} status={} timestamp={}",
                event.getReferenceId(), event.getStatus(), eventTs);

        Optional<Payment> existing =
                paymentRepository.findByReferenceIdForUpdate(event.getReferenceId());

        if (existing.isEmpty()) {
            Payment payment = Payment.builder()
                    .referenceId(event.getReferenceId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .debtorName(event.getDebtorName())
                    .debtorIban(event.getDebtorIban())
                    .creditorIban(event.getCreditorIban())
                    .valueDate(event.getValueDate())
                    .status(event.getStatus())
                    .eventTimestamp(eventTs)
                    .build();

            paymentRepository.save(payment);

            log.info("Inserted new payment: referenceId={} status={}",
                    event.getReferenceId(), event.getStatus());
            return;
        }

        Payment payment = existing.get();

        Instant currentTs = payment.getEventTimestamp() != null
                ? payment.getEventTimestamp()
                : Instant.EPOCH;

        if (payment.getStatus() == event.getStatus()
                && Objects.equals(currentTs, eventTs)) {

            log.debug("Duplicate ignored: referenceId={} status={} timestamp={}",
                    event.getReferenceId(), event.getStatus(), eventTs);
            return;
        }

        if (payment.getStatus() == event.getStatus()
                && eventTs.isAfter(currentTs)) {

            log.info("Updating timestamp only: referenceId={} {} -> {}",
                    event.getReferenceId(), currentTs, eventTs);

            payment.setEventTimestamp(eventTs);
            paymentRepository.save(payment);
            return;
        }

        if (eventTs.isAfter(currentTs)) {

            log.info("Updating payment: referenceId={} status {} -> {}",
                    event.getReferenceId(), payment.getStatus(), event.getStatus());

            payment.setStatus(event.getStatus());
            payment.setEventTimestamp(eventTs);
            paymentRepository.save(payment);
            return;
        }

        log.warn("Stale event ignored: referenceId={} incomingStatus={} incomingTimestamp={} currentTimestamp={}",
                event.getReferenceId(),
                event.getStatus(),
                eventTs,
                currentTs);
    }
}