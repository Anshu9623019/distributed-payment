package com.distributed_payment.auth.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs
) {}
