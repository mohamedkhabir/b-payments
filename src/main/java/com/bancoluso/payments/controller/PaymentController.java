package com.bancoluso.payments.controller;

import com.bancoluso.payments.dto.DynamicResponse;
import com.bancoluso.payments.dto.PaymentRequest;
import com.bancoluso.payments.dto.PaymentResponse;
import com.bancoluso.payments.service.PaymentIngestionService;
import com.bancoluso.payments.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Real-time payment status API")
public class PaymentController {

    private final PaymentIngestionService ingestionService;
    private final PaymentQueryService queryService;
    @PostMapping("/events")
    @Operation(summary = "Ingest a payment status event")
    public ResponseEntity<Void> ingestEvent(@Valid @RequestBody PaymentRequest request) {
        ingestionService.ingest(request);
        return ResponseEntity.accepted().build();
    }
    @GetMapping("/{referenceId}/status")
    @Operation(summary = "Get current status of a payment")
    public ResponseEntity<PaymentResponse> getStatus(@PathVariable String referenceId) {
        return ResponseEntity.ok(queryService.getStatus(referenceId));
    }
    @GetMapping
    @Operation(summary = "List all payments with pagination")
    public ResponseEntity<DynamicResponse> listPayments(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.listPayments(pageable));
    }
}
