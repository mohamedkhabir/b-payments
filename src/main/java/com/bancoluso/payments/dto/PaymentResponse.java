package com.bancoluso.payments.dto;

import com.bancoluso.payments.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class PaymentResponse {
    private String referenceId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String debtorName;
    private String debtorIban;
    private String creditorIban;
    private LocalDate valueDate;
    private Instant eventTimestamp;
    private Instant updatedAt;
}
