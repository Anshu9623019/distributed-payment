package com.distributed_payment.payment.event;

import java.math.BigDecimal;

public record PaymentFailedEvent(
        String paymentId,
        Long senderId,
        String senderType,
        BigDecimal amount,
        String failureReason
) {}
