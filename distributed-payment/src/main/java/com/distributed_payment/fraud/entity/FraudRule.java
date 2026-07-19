package com.distributed_payment.fraud.entity;

public enum FraudRule {
    VELOCITY_LIMIT,   // too many payments from the same sender in a short window
    LARGE_AMOUNT,     // amount above the auto-review threshold
    BLACKLISTED_IP    // request originated from a known-bad IP
}
