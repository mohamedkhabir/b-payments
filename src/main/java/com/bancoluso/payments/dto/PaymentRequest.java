package com.bancoluso.payments.dto;

import com.bancoluso.payments.entity.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class PaymentRequest {
    @NotBlank(message = "referenceId is required")
    @Size(max = 100)
    private String referenceId;
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;
    @NotBlank(message = "debtorName is required")
    private String debtorName;
    @NotBlank(message = "debtorIban is required")
    private String debtorIban;
    @NotBlank(message = "creditorIban is required")
    private String creditorIban;
    @NotNull(message = "valueDate is required")
    private LocalDate valueDate;
    @NotNull(message = "status is required")
    private PaymentStatus status;
    @NotNull(message = "eventTimestamp is required")
    private Instant eventTimestamp;
}
