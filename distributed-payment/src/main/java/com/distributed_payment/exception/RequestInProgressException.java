package com.distributed_payment.exception;

/**
 * Thrown when a concurrent request with the same idempotency key is still
 * being processed. Callers should retry after a short delay.
 */
public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String message) {
        super(message);
    }
}
