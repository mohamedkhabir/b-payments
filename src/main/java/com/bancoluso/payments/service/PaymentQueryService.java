package com.bancoluso.payments.service;

import com.bancoluso.payments.dto.DynamicResponse;
import com.bancoluso.payments.dto.PaymentResponse;
import com.bancoluso.payments.entity.Payment;
import com.bancoluso.payments.exception.PaymentNotFoundException;
import com.bancoluso.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentQueryService {
    private final PaymentRepository paymentRepository;
    @Transactional(readOnly = true)
    public PaymentResponse getStatus(String referenceId) {
        log.info("Querying payment status: referenceId={}", referenceId);
        Payment payment = paymentRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new PaymentNotFoundException(referenceId));
        return toResponse(payment);
    }
    @Transactional(readOnly = true)
    public DynamicResponse listPayments(Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<PaymentResponse> page = paymentRepository.findAllPaged(unsorted)
                .map(this::toResponse);

        return DynamicResponse.builder()
                .result(page.getContent())
                .currentPage(page.getNumber())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .referenceId(p.getReferenceId())
                .status(p.getStatus())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .debtorName(p.getDebtorName())
                .debtorIban(p.getDebtorIban())
                .creditorIban(p.getCreditorIban())
                .valueDate(p.getValueDate())
                .eventTimestamp(p.getEventTimestamp())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
