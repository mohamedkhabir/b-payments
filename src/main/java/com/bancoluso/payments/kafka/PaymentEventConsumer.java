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
        log.info("Received payment event: referenceId={} status={} timestamp={}", event.getReferenceId(), event.getStatus(), event.getEventTimestamp());
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
                    .eventTimestamp(event.getEventTimestamp())
                    .build();
            paymentRepository.save(payment);
            log.info("Inserted new payment: referenceId={} status={}", event.getReferenceId(), event.getStatus());
            return;
        }
        Payment payment = existing.get();
        if (payment.getStatus() == event.getStatus()) {
            log.debug("Duplicate event ignored: referenceId={} status={}", event.getReferenceId(), event.getStatus());
            return;
        }
        if (event.getEventTimestamp().isAfter(payment.getEventTimestamp())) {
            log.info("Updating payment status: referenceId={} {} -> {}",
                    event.getReferenceId(), payment.getStatus(), event.getStatus());
            payment.setStatus(event.getStatus());
            payment.setEventTimestamp(event.getEventTimestamp());
            paymentRepository.save(payment);
        } else {
            log.warn("Stale event ignored: referenceId={} incomingStatus={} incomingTimestamp={} currentTimestamp={}",
                    event.getReferenceId(), event.getStatus(),
                    event.getEventTimestamp(), payment.getEventTimestamp());
        }
    }
}
