package com.bancoluso.payments.service;

import com.bancoluso.payments.dto.PaymentRequest;
import com.bancoluso.payments.kafka.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentIngestionService {
    private final PaymentEventProducer producer;
    public void ingest(PaymentRequest request) {
        log.info("Ingesting payment event: referenceId={}", request.getReferenceId());
        producer.publish(request);
    }
}
