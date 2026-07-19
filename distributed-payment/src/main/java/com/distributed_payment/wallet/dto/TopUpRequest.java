package com.distributed_payment.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum top-up is 1.00")
        BigDecimal amount
) {}
