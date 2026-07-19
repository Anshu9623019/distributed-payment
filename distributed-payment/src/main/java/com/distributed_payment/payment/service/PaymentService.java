//package com.distributed_payment.payment.service;
//
//import com.distributed_payment.exception.ResourceNotFoundException;
//import com.distributed_payment.outbox.entity.OutboxEvent;
//import com.distributed_payment.outbox.entity.OutboxStatus;
//import com.distributed_payment.outbox.repository.OutboxEventRepository;
//import com.distributed_payment.payment.entity.Payment;
//import com.distributed_payment.payment.entity.PaymentStatus;
//import com.distributed_payment.payment.event.PaymentInitiatedEvent;
//import com.distributed_payment.payment.repo.PaymentRepository;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class PaymentService {
//
//    private static final String TOPIC_INITIATED = "payment-initiated";
//
//    private final PaymentRepository paymentRepository;
//    private final OutboxEventRepository outboxEventRepository;
//    private final IdempotencyService idempotencyService;
//    private final ObjectMapper objectMapper;
//
//    public Payment initiatePayment(Long senderId, String senderType, Long receiverId, String receiverType,
//                                    BigDecimal amount, String currency, String idempotencyKey) {
//
//        // 1. Fast path: this exact request already completed. Return the same result.
//        var cached = idempotencyService.getCachedResponse(idempotencyKey);
//        if (cached.isPresent()) {
//            log.info("Idempotent replay for key={}, returning cached response", idempotencyKey);
//            return deserialize(cached.get());
//        }
//
//        // 2. DB is the source of truth in case the Redis cache entry expired
//        //    but the payment was in fact already created.
//        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
//        if (existing.isPresent()) {
//            return existing.get();
//        }
//
//        // 3. Guard against a genuinely concurrent duplicate request for this key.
//        idempotencyService.acquireProcessingLock(idempotencyKey);
//
//        try {
//            Payment payment = createPaymentAndOutboxEvent(
//                    senderId, senderType, receiverId, receiverType, amount, currency, idempotencyKey);
//
//            idempotencyService.cacheResponse(idempotencyKey, serialize(payment));
//            return payment;
//        } catch (RuntimeException ex) {
//            // Release the lock on failure so the client can retry immediately
//            // rather than waiting out the full lock TTL.
//            idempotencyService.releaseLock(idempotencyKey);
//            throw ex;
//        }
//    }
//
//    @Transactional
//    protected Payment createPaymentAndOutboxEvent(Long senderId, String senderType, Long receiverId, String receiverType,
//                                                   BigDecimal amount, String currency, String idempotencyKey) {
//        String paymentId = UUID.randomUUID().toString();
//
//        Payment payment = Payment.builder()
//                .id(paymentId)
//                .senderOwnerId(senderId)
//                .senderOwnerType(senderType)
//                .receiverOwnerId(receiverId)
//                .receiverOwnerType(receiverType)
//                .amount(amount)
//                .currency(currency)
//                .status(PaymentStatus.INITIATED)
//                .idempotencyKey(idempotencyKey)
//                .build();
//        paymentRepository.save(payment);
//
//        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
//                paymentId, senderId, senderType, receiverId, receiverType, amount, currency);
//
//        // Outbox write happens in the SAME transaction as the payment insert.
//        // Either both commit or neither does — no window where a payment
//        // exists with no corresponding event, and no direct Kafka call here
//        // that could fail independently of the DB commit.
//        OutboxEvent outboxEvent = OutboxEvent.builder()
//                .aggregateId(paymentId)
//                .topic(TOPIC_INITIATED)
//                .eventType("PaymentInitiatedEvent")
//                .payload(serialize(event))
//                .status(OutboxStatus.PENDING)
//                .build();
//        outboxEventRepository.save(outboxEvent);
//
//        log.info("Created payment id={} and queued PaymentInitiatedEvent via outbox", paymentId);
//        return payment;
//    }
//
//    private String serialize(Object obj) {
//        try {
//            return objectMapper.writeValueAsString(obj);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Failed to serialize event/response", e);
//        }
//    }
//
//    private Payment deserialize(String json) {
//        try {
//            return objectMapper.readValue(json, Payment.class);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Failed to deserialize cached payment response", e);
//        }
//    }
//}



package com.distributed_payment.payment.service;

import com.distributed_payment.exception.PaymentBlockedException;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.fraud.entity.FraudAlert;
import com.distributed_payment.fraud.entity.FraudCheckResult;
import com.distributed_payment.fraud.repository.FraudAlertRepository;
import com.distributed_payment.fraud.service.FraudService;
import com.distributed_payment.outbox.entity.OutboxEvent;
import com.distributed_payment.outbox.entity.OutboxStatus;
import com.distributed_payment.outbox.repository.OutboxEventRepository;
import com.distributed_payment.payment.entity.Payment;
import com.distributed_payment.payment.entity.PaymentStatus;
import com.distributed_payment.payment.event.PaymentInitiatedEvent;
import com.distributed_payment.payment.repo.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String TOPIC_INITIATED = "payment-initiated";

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final FraudService fraudService;
    private final FraudAlertRepository fraudAlertRepository;
    private final ObjectMapper objectMapper;

    public Payment initiatePayment(Long senderId, String senderType, Long receiverId, String receiverType,
                                   BigDecimal amount, String currency, String idempotencyKey, String ipAddress) {

        // 1. Fast path: this exact request already completed. Return the same result.
        var cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotent replay for key={}, returning cached response", idempotencyKey);
            return deserialize(cached.get());
        }

        // 2. DB is the source of truth in case the Redis cache entry expired
        //    but the payment was in fact already created.
        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 3. Fraud check runs BEFORE the idempotency lock and BEFORE anything
        //    is persisted, so a blocked attempt never creates a payment row
        //    at all — only an audit trail in fraud_alerts.
        FraudCheckResult fraudResult = fraudService.evaluate(senderId, senderType, amount, ipAddress);

        if (fraudResult.isBlocked()) {
            fraudAlertRepository.save(FraudAlert.builder()
                    .paymentId(null)
                    .senderOwnerId(senderId)
                    .senderOwnerType(senderType)
                    .ruleTriggered(fraudResult.ruleTriggered())
                    .decision(fraudResult.decision())
                    .details(fraudResult.details())
                    .build());
            throw new PaymentBlockedException("Payment blocked: " + fraudResult.details());
        }

        // 4. Guard against a genuinely concurrent duplicate request for this key.
        idempotencyService.acquireProcessingLock(idempotencyKey);

        try {
            Payment payment = createPayment(
                    senderId, senderType, receiverId, receiverType, amount, currency, idempotencyKey, fraudResult);

            idempotencyService.cacheResponse(idempotencyKey, serialize(payment));
            return payment;
        } catch (RuntimeException ex) {
            // Release the lock on failure so the client can retry immediately
            // rather than waiting out the full lock TTL.
            idempotencyService.releaseLock(idempotencyKey);
            throw ex;
        }
    }

    @Transactional
    protected Payment createPayment(Long senderId, String senderType, Long receiverId, String receiverType,
                                    BigDecimal amount, String currency, String idempotencyKey,
                                    FraudCheckResult fraudResult) {
        String paymentId = UUID.randomUUID().toString();
        boolean heldForReview = fraudResult.requiresReview();

        Payment payment = Payment.builder()
                .id(paymentId)
                .senderOwnerId(senderId)
                .senderOwnerType(senderType)
                .receiverOwnerId(receiverId)
                .receiverOwnerType(receiverType)
                .amount(amount)
                .currency(currency)
                .status(heldForReview ? PaymentStatus.HELD_FOR_REVIEW : PaymentStatus.INITIATED)
                .idempotencyKey(idempotencyKey)
                .build();
        paymentRepository.save(payment);

        if (heldForReview) {
            // Money does not move until an admin approves this via
            // FraudAdminController — no outbox event is queued yet.
            fraudAlertRepository.save(FraudAlert.builder()
                    .paymentId(paymentId)
                    .senderOwnerId(senderId)
                    .senderOwnerType(senderType)
                    .ruleTriggered(fraudResult.ruleTriggered())
                    .decision(fraudResult.decision())
                    .details(fraudResult.details())
                    .build());
            log.warn("Payment id={} HELD_FOR_REVIEW: {}", paymentId, fraudResult.details());
            return payment;
        }

        queueInitiatedEvent(paymentId, senderId, senderType, receiverId, receiverType, amount, currency);
        log.info("Created payment id={} and queued PaymentInitiatedEvent via outbox", paymentId);
        return payment;
    }

    /**
     * Called either right after a clean payment is created, or later by
     * FraudAdminController when a HELD_FOR_REVIEW payment is approved —
     * either way the outbox write happens in the same transaction as the
     * status change that authorizes it.
     */
    @Transactional
    public void queueInitiatedEvent(String paymentId, Long senderId, String senderType,
                                    Long receiverId, String receiverType, BigDecimal amount, String currency) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                paymentId, senderId, senderType, receiverId, receiverType, amount, currency);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(paymentId)
                .topic(TOPIC_INITIATED)
                .eventType("PaymentInitiatedEvent")
                .payload(serialize(event))
                .status(OutboxStatus.PENDING)
                .build();
        outboxEventRepository.save(outboxEvent);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event/response", e);
        }
    }

    private Payment deserialize(String json) {
        try {
            return objectMapper.readValue(json, Payment.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached payment response", e);
        }
    }
}
