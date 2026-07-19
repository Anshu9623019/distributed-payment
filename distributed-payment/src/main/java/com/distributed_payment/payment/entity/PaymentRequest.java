package com.distributed_payment.payment.entity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "Sender ID is required")
        Long senderId,

        @NotBlank(message = "Sender type is required (CUSTOMER/MERCHANT)")
        String senderType,

        @NotNull(message = "Receiver ID is required")
        Long receiverId,

        @NotBlank(message = "Receiver type is required (CUSTOMER/MERCHANT)")
        String receiverType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {}
