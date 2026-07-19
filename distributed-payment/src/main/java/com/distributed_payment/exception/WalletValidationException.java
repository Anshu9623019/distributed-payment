package com.distributed_payment.exception;

/**
 * Thrown when a business validation rule fails within the wallet domain
 * (e.g., insufficient balance, account frozen, invalid currency).
 *
 * Used with @Transactional(noRollbackFor = WalletValidationException.class)
 * to prevent database transaction corruption during expected business failures.
 */
public class WalletValidationException extends RuntimeException {

    public WalletValidationException(String message) {
        super(message);
    }

    public WalletValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}