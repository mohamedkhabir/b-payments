package com.bancoluso.payments.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String referenceId) {
        super("Payment not found: " + referenceId);
    }
}
