package com.bancoluso.payments.unit;

import com.bancoluso.payments.dto.PaymentRequest;
import com.bancoluso.payments.entity.Payment;
import com.bancoluso.payments.entity.PaymentStatus;
import com.bancoluso.payments.kafka.PaymentEventConsumer;
import com.bancoluso.payments.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer — Idempotency Rules")
class PaymentEventConsumerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentEventConsumer consumer;

    private static final String REFERENCE_ID = "TXN-2026-0001";
    private static final Instant BASE_TIME = Instant.parse("2026-04-14T09:00:00Z");

    private PaymentRequest buildRequest(PaymentStatus status, Instant timestamp) {
        PaymentRequest req = new PaymentRequest();
        req.setReferenceId(REFERENCE_ID);
        req.setAmount(new BigDecimal("1500.00"));
        req.setCurrency("EUR");
        req.setDebtorName("Mohamed khabir");
        req.setDebtorIban("PT50000201231234567890154");
        req.setCreditorIban("PT50000201239876543210154");
        req.setValueDate(LocalDate.of(2026, 4, 14));
        req.setStatus(status);
        req.setEventTimestamp(timestamp);
        return req;
    }

    private Payment existingPayment(PaymentStatus status, Instant timestamp) {
        return Payment.builder()
                .referenceId(REFERENCE_ID)
                .amount(new BigDecimal("1500.00"))
                .currency("EUR")
                .debtorName("Mohamed khabir")
                .debtorIban("PT50000201231234567890154")
                .creditorIban("PT50000201239876543210154")
                .valueDate(LocalDate.of(2026, 4, 14))
                .status(status)
                .eventTimestamp(timestamp)
                .build();
    }
    @Test
    @DisplayName("Rule 4 : New referenceId → insert payment")
    void newPayment_shouldInsert() {
        when(paymentRepository.findByReferenceIdForUpdate(REFERENCE_ID)).thenReturn(Optional.empty());
        consumer.consume(buildRequest(PaymentStatus.PENDING, BASE_TIME));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(REFERENCE_ID);
    }
    @Test
    @DisplayName("Rule 1 : Same referenceId + same status → silently ignore")
    void trueDuplicate_shouldIgnore() {
        Payment existing = existingPayment(PaymentStatus.PENDING, BASE_TIME);
        when(paymentRepository.findByReferenceIdForUpdate(REFERENCE_ID)).thenReturn(Optional.of(existing));
        consumer.consume(buildRequest(PaymentStatus.PENDING, BASE_TIME));
        verify(paymentRepository, never()).save(any());
    }
    @Test
    @DisplayName("Rule 2 : Same referenceId + different status + newer timestamp → update status")
    void newerEvent_differentStatus_shouldUpdateStatus() {
        Payment existing = existingPayment(PaymentStatus.PENDING, BASE_TIME);
        when(paymentRepository.findByReferenceIdForUpdate(REFERENCE_ID)).thenReturn(Optional.of(existing));
        Instant newerTimestamp = BASE_TIME.plusSeconds(60);
        consumer.consume(buildRequest(PaymentStatus.PROCESSING, newerTimestamp));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(captor.getValue().getEventTimestamp()).isEqualTo(newerTimestamp);
    }

    @Test
    @DisplayName("Rule 2 : Financial fields must remain immutable after status update")
    void statusUpdate_shouldNotChangeFinancialFields() {
        BigDecimal originalAmount = new BigDecimal("1500.00");
        Payment existing = existingPayment(PaymentStatus.PENDING, BASE_TIME);
        existing.setAmount(originalAmount);
        when(paymentRepository.findByReferenceIdForUpdate(REFERENCE_ID)).thenReturn(Optional.of(existing));
        Instant newerTimestamp = BASE_TIME.plusSeconds(60);
        PaymentRequest req = buildRequest(PaymentStatus.PROCESSING, newerTimestamp);
        req.setAmount(new BigDecimal("9999.00"));
        consumer.consume(req);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(originalAmount);
    }
    @Test
    @DisplayName("Rule 3 : Same referenceId + different status + older timestamp → silently ignore")
    void staleEvent_shouldIgnore() {
        Payment existing = existingPayment(PaymentStatus.PROCESSING, BASE_TIME.plusSeconds(120));
        when(paymentRepository.findByReferenceIdForUpdate(REFERENCE_ID)).thenReturn(Optional.of(existing));
        Instant olderTimestamp = BASE_TIME;
        consumer.consume(buildRequest(PaymentStatus.PENDING, olderTimestamp));
        verify(paymentRepository, never()).save(any());
    }
}
