package com.distributed_payment.settlement.service;

public record BankTransferResult(boolean success, String bankReference, String failureReason) {

    public static BankTransferResult success(String bankReference) {
        return new BankTransferResult(true, bankReference, null);
    }

    public static BankTransferResult failure(String reason) {
        return new BankTransferResult(false, null, reason);
    }
}
