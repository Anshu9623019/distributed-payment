package com.distributed_payment.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A durable record of every non-APPROVE fraud decision, whether or not a
 * Payment row exists yet (a BLOCK happens before the payment is created).
 * This is the audit trail and manual-review queue in one table.
 */
@Entity
@Table(name = "fraud_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id")
    private String paymentId; // null for BLOCKed attempts, since no payment was ever created

    @Column(name = "sender_owner_id", nullable = false)
    private Long senderOwnerId;

    @Column(name = "sender_owner_type", nullable = false, length = 20)
    private String senderOwnerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_triggered", nullable = false)
    private FraudRule ruleTriggered;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudDecision decision;

    @Column(nullable = false)
    private String details;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
