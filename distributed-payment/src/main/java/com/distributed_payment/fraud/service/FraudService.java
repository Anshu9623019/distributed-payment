package com.distributed_payment.fraud.service;

import com.distributed_payment.fraud.entity.FraudCheckResult;
import com.distributed_payment.fraud.entity.FraudDecision;
import com.distributed_payment.fraud.entity.FraudRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Rule-based fraud checks, evaluated in strictly increasing severity so a
 * BLOCK is never masked by a lower-severity finding. Rules are simple and
 * synchronous by design (a real system would also run async, model-based
 * scoring afterward) — this is the "fast fail" layer that runs inline with
 * payment initiation, before anything is persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudService {

    private static final int VELOCITY_LIMIT = 10;
    private static final Duration VELOCITY_WINDOW = Duration.ofSeconds(30);
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000");

    private static final String BLACKLIST_KEY = "fraud:blacklist:ip";

    private final RedisTemplate<String, Object> redisTemplate;

    public FraudCheckResult evaluate(Long senderOwnerId, String senderOwnerType, BigDecimal amount, String ipAddress) {
        FraudCheckResult ipCheck = checkBlacklistedIp(ipAddress);
        if (ipCheck.isBlocked()) return ipCheck;

        FraudCheckResult velocityCheck = checkVelocity(senderOwnerId, senderOwnerType);
        if (velocityCheck.isBlocked()) return velocityCheck;

        FraudCheckResult amountCheck = checkLargeAmount(amount);
        if (amountCheck.requiresReview()) return amountCheck;

        return FraudCheckResult.APPROVED;
    }

    private FraudCheckResult checkBlacklistedIp(String ipAddress) {
        if (ipAddress == null) return FraudCheckResult.APPROVED;

        Boolean isMember = redisTemplate.opsForSet().isMember(BLACKLIST_KEY, ipAddress);
        if (Boolean.TRUE.equals(isMember)) {
            log.warn("Blocked payment attempt from blacklisted IP: {}", ipAddress);
            return new FraudCheckResult(FraudDecision.BLOCK, FraudRule.BLACKLISTED_IP,
                    "Request originated from a blacklisted IP: " + ipAddress);
        }
        return FraudCheckResult.APPROVED;
    }

    /**
     * Sliding window via a Redis sorted set: each attempt is a member scored
     * by its own timestamp, so pruning members older than the window is a
     * single ZREMRANGEBYSCORE and counting what's left is a single ZCARD —
     * no separate cleanup job needed.
     */
    private FraudCheckResult checkVelocity(Long senderOwnerId, String senderOwnerType) {
        String key = "fraud:velocity:" + senderOwnerType + ":" + senderOwnerId;
        long now = System.currentTimeMillis();
        long windowStart = now - VELOCITY_WINDOW.toMillis();

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);
        redisTemplate.expire(key, VELOCITY_WINDOW.plusSeconds(5));

        Long count = redisTemplate.opsForZSet().zCard(key);
        if (count != null && count > VELOCITY_LIMIT) {
            log.warn("Blocked payment: sender {} {} made {} payments in {}s (limit {})",
                    senderOwnerType, senderOwnerId, count, VELOCITY_WINDOW.getSeconds(), VELOCITY_LIMIT);
            return new FraudCheckResult(FraudDecision.BLOCK, FraudRule.VELOCITY_LIMIT,
                    count + " payments in " + VELOCITY_WINDOW.getSeconds() + "s (limit " + VELOCITY_LIMIT + ")");
        }
        return FraudCheckResult.APPROVED;
    }

    private FraudCheckResult checkLargeAmount(BigDecimal amount) {
        if (amount.compareTo(LARGE_AMOUNT_THRESHOLD) >= 0) {
            return new FraudCheckResult(FraudDecision.MANUAL_REVIEW, FraudRule.LARGE_AMOUNT,
                    "Amount " + amount + " meets or exceeds the review threshold of " + LARGE_AMOUNT_THRESHOLD);
        }
        return FraudCheckResult.APPROVED;
    }
}
