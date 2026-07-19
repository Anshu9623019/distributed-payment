package com.distributed_payment.merchant.dto;

public record MerchantSummaryResponse(
        Long id,
        String businessName,
        String apiKey,
        String webhookUrl,
        String settlementAccount,
        String status
) {}