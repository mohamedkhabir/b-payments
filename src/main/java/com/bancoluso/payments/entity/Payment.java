package com.bancoluso.payments.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payments_reference_id", columnList = "reference_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reference_id", nullable = false, unique = true, length = 100)
    private String referenceId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "debtor_name", nullable = false, length = 255)
    private String debtorName;
    @Column(name = "debtor_iban", nullable = false, length = 34)
    private String debtorIban;
    @Column(name = "creditor_iban", nullable = false, length = 34)
    private String creditorIban;
    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
