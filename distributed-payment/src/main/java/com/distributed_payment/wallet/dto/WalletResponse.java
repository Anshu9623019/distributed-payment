package com.distributed_payment.wallet.dto;

import java.math.BigDecimal;

public record WalletResponse(
        Long walletId,
        Long ownerId,
        String ownerType,
        BigDecimal balance,
        String currency
) {}
