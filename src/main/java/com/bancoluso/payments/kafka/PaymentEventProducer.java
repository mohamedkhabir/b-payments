package com.bancoluso.payments.kafka;

import com.bancoluso.payments.config.KafkaConfig;
import com.bancoluso.payments.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {
    private final KafkaTemplate<String, PaymentRequest> kafkaTemplate;
    public void publish(PaymentRequest event) {
        log.info("Publishing payment event to Kafka: referenceId={} status={}", event.getReferenceId(), event.getStatus());
        kafkaTemplate.send(KafkaConfig.PAYMENT_EVENTS_TOPIC, event.getReferenceId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment event: referenceId={}", event.getReferenceId(), ex);
                    } else {
                        log.debug("Payment event published: referenceId={} partition={} offset={}",
                                event.getReferenceId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
