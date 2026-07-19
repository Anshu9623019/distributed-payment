package com.distributed_payment.exception;

/**
 * Thrown when a payment event triggers a fraud detection rule or
 * risk mitigation block, halting processing immediately.
 */
public class PaymentBlockedException extends RuntimeException {

    public PaymentBlockedException(String message) {
        super(message);
    }

    public PaymentBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}