package com.distributed_payment.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryItem(
        String id,
        String direction, // SENT or RECEIVED, relative to the wallet that asked
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        LocalDateTime createdAt
) {}
