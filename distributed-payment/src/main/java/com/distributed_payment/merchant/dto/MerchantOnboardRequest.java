package com.distributed_payment.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record MerchantOnboardRequest(
        @NotBlank(message = "Business name is required")
        String businessName,

        @URL(message = "Must be a valid URL")
        String webhookUrl,

        String settlementAccount
) {}