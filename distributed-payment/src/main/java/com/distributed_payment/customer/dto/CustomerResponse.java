package com.distributed_payment.customer.dto;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String address,
        boolean enabled
) {}
