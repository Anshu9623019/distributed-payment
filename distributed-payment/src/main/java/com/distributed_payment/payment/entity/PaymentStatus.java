package com.distributed_payment.payment.entity;

public enum PaymentStatus {
    INITIATED,
    HELD_FOR_REVIEW, // fraud engine flagged this for manual approval; no outbox event yet
    PROCESSING,
    SUCCESS,
    FAILED,
    REVERSED
}