package com.distributed_payment.merchant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "merchants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "api_key", nullable = false, unique = true, length = 64)
    private String apiKey;

    @Column(name = "api_secret", nullable = false, length = 128)
    private String apiSecret;

    @Column(name = "webhook_url")
    private String webhookUrl;

    // Where settlement payouts are sent. In a real system this would be a
    // tokenized reference into a bank-account vault, never a raw account
    // number stored in plaintext.
    @Column(name = "settlement_account")
    private String settlementAccount;

    @Column(nullable = false)
    private String status; // ACTIVE, SUSPENDED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}