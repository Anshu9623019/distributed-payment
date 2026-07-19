package com.distributed_payment.fraud.controller;

import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.fraud.dto.RejectRequest;
import com.distributed_payment.fraud.entity.FraudAlert;
import com.distributed_payment.fraud.repository.FraudAlertRepository;
import com.distributed_payment.payment.entity.Payment;
import com.distributed_payment.payment.entity.PaymentStatus;
import com.distributed_payment.payment.repo.PaymentRepository;
import com.distributed_payment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * The manual-review side of fraud detection: an ops/admin queue for
 * payments the rule engine held (HELD_FOR_REVIEW) rather than auto-approved
 * or auto-blocked, plus IP blacklist management.
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FraudAdminController {

    private static final String BLACKLIST_KEY = "fraud:blacklist:ip";

    private final FraudAlertRepository fraudAlertRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/alerts")
    public List<FraudAlert> listUnresolved() {
        return fraudAlertRepository.findByResolvedFalseOrderByCreatedAtAsc();
    }

    @PostMapping("/alerts/{id}/approve")
    @Transactional
    public FraudAlert approve(@PathVariable Long id) {
        FraudAlert alert = fraudAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found: " + id));

        if (alert.getPaymentId() != null) {
            Payment payment = paymentRepository.findById(alert.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + alert.getPaymentId()));

            if (payment.getStatus() == PaymentStatus.HELD_FOR_REVIEW) {
                payment.setStatus(PaymentStatus.INITIATED);
                paymentRepository.save(payment);

                // Only now does the outbox event get queued — approval is what
                // actually authorizes the money movement to proceed.
                paymentService.queueInitiatedEvent(
                        payment.getId(), payment.getSenderOwnerId(), payment.getSenderOwnerType(),
                        payment.getReceiverOwnerId(), payment.getReceiverOwnerType(),
                        payment.getAmount(), payment.getCurrency());
            }
        }

        alert.setResolved(true);
        return fraudAlertRepository.save(alert);
    }

    @PostMapping("/alerts/{id}/reject")
    @Transactional
    public FraudAlert reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        FraudAlert alert = fraudAlertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud alert not found: " + id));

        if (alert.getPaymentId() != null) {
            paymentRepository.findById(alert.getPaymentId()).ifPresent(payment -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setErrorMessage("Rejected during manual fraud review"
                        + (request != null && request.reason() != null ? ": " + request.reason() : ""));
                paymentRepository.save(payment);
            });
        }

        alert.setResolved(true);
        return fraudAlertRepository.save(alert);
    }

    @GetMapping("/blacklist")
    public Set<Object> listBlacklistedIps() {
        return redisTemplate.opsForSet().members(BLACKLIST_KEY);
    }

    @PostMapping("/blacklist/{ip}")
    public void blacklistIp(@PathVariable String ip) {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, ip);
    }

    @DeleteMapping("/blacklist/{ip}")
    public void removeFromBlacklist(@PathVariable String ip) {
        redisTemplate.opsForSet().remove(BLACKLIST_KEY, ip);
    }
}