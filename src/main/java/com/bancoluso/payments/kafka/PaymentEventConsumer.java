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
        log.info("Received payment event: referenceId={} status={} timestamp={}", event.getReferenceId(), event.getStatus(), eventTs);
        Optional<Payment> existing = paymentRepository.findByReferenceIdForUpdate(event.getReferenceId());

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
            log.info("Inserted new payment: referenceId={} status={}", event.getReferenceId(), event.getStatus());
            return;
        }
        Payment payment = existing.get();

        if (payment.getStatus() == event.getStatus()
                && payment.getEventTimestamp().equals(eventTs)) {

            log.debug("True duplicate ignored: referenceId={} status={} timestamp={}",
                    event.getReferenceId(), event.getStatus(), eventTs);
            return;
        }

        if (payment.getStatus() == event.getStatus()
                && eventTs.isAfter(payment.getEventTimestamp())) {

            log.info("Updating timestamp only (same status): referenceId={} timestamp {} -> {}",
                    event.getReferenceId(), payment.getEventTimestamp(), eventTs);

            payment.setEventTimestamp(eventTs);
            paymentRepository.save(payment);
            return;
        }

        if (eventTs.isAfter(payment.getEventTimestamp())) {

            log.info("Updating payment status: referenceId={} {} -> {}",
                    event.getReferenceId(), payment.getStatus(), event.getStatus());

            payment.setStatus(event.getStatus());
            payment.setEventTimestamp(eventTs);
            paymentRepository.save(payment);
            return;
        }

        log.warn("Stale event ignored: referenceId={} incomingStatus={} incomingTimestamp={} currentTimestamp={}",
                event.getReferenceId(), event.getStatus(),
                eventTs, payment.getEventTimestamp());
    }
}
