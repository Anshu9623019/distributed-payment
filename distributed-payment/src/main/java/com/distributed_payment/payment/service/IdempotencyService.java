package com.distributed_payment.payment.service;

import com.distributed_payment.exception.RequestInProgressException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Two-tier idempotency:
 *  - a short-lived "processing" lock (SETNX) that rejects genuinely concurrent
 *    duplicate requests fast, without hitting the DB
 *  - a long-lived cached RESPONSE keyed by the same idempotency key, so a
 *    retried request gets back the exact same result instead of an error
 *
 * The critical fix versus a naive implementation: acquiring the lock does
 * NOT mean "reject this request". It only guards against two requests
 * processing the same key at once. Once a request completes, its response
 * is cached so retries are served from cache, not rejected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration RESPONSE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private static String responseKey(String idempotencyKey) {
        return "idempotency:response:" + idempotencyKey;
    }

    private static String lockKey(String idempotencyKey) {
        return "idempotency:lock:" + idempotencyKey;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> getCachedResponse(String idempotencyKey) {
        Object value = redisTemplate.opsForValue().get(responseKey(idempotencyKey));
        return Optional.ofNullable((String) value);
    }

    /**
     * Call before starting work. Throws if another request with the same key
     * is currently in flight (caller should surface this as HTTP 409 and let
     * the client retry shortly).
     */
    public void acquireProcessingLock(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(idempotencyKey), "PROCESSING", LOCK_TTL);

        if (Boolean.FALSE.equals(acquired)) {
            throw new RequestInProgressException(
                    "A request with idempotency key [" + idempotencyKey + "] is already being processed. Retry shortly.");
        }
    }

    /** Call once the request has completed successfully. Caches the response and releases the lock. */
    public void cacheResponse(String idempotencyKey, String responseJson) {
        redisTemplate.opsForValue().set(responseKey(idempotencyKey), responseJson, RESPONSE_TTL);
        redisTemplate.delete(lockKey(idempotencyKey));
    }

    /** Call if processing fails so the client can retry immediately instead of waiting out the lock TTL. */
    public void releaseLock(String idempotencyKey) {
        redisTemplate.delete(lockKey(idempotencyKey));
    }
}
