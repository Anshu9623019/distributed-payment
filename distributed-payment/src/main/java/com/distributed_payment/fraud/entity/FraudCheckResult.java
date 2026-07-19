package com.distributed_payment.fraud.entity;

public record FraudCheckResult(FraudDecision decision, FraudRule ruleTriggered, String details) {

    public static final FraudCheckResult APPROVED = new FraudCheckResult(FraudDecision.APPROVE, null, null);

    public boolean isBlocked() {
        return decision == FraudDecision.BLOCK;
    }

    public boolean requiresReview() {
        return decision == FraudDecision.MANUAL_REVIEW;
    }
}
