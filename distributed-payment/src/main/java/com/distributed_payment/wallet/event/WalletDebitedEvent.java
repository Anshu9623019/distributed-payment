package com.distributed_payment.wallet.event;

import java.math.BigDecimal;

public record WalletDebitedEvent(
        String paymentId,
        Long senderId,
        String senderType,
        Long receiverId,
        String receiverType,
        BigDecimal amount
) {}
