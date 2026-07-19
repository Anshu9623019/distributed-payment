package com.distributed_payment.payment.event;

import java.math.BigDecimal;

public record PaymentInitiatedEvent(
        String paymentId,
        Long senderId,
        String senderType,
        Long receiverId,
        String receiverType,
        BigDecimal amount,
        String currency
) {}



