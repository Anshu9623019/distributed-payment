package com.distributed_payment.payment.controller;

import com.distributed_payment.customer.repository.CustomerRepository;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.payment.entity.Payment;
import com.distributed_payment.payment.entity.PaymentHistoryItem;
import com.distributed_payment.payment.entity.PaymentRequest;
import com.distributed_payment.payment.repo.PaymentRepository;
import com.distributed_payment.payment.service.PaymentService;
import com.distributed_payment.security.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final MerchantRepository merchantRepository;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Payment> initiatePayment(@Valid @RequestBody PaymentRequest request, HttpServletRequest httpRequest) {
        Payment payment = paymentService.initiatePayment(
                request.senderId(),
                request.senderType(),
                request.receiverId(),
                request.receiverType(),
                request.amount(),
                request.currency(),
                request.idempotencyKey(),
                clientIp(httpRequest)
        );

        // 202 Accepted: the actual debit/credit/settlement happens asynchronously via Kafka.
        // (Or, if held for fraud review, nothing happens yet — see payment.status.)
        return ResponseEntity.accepted().body(payment);
    }

    // Trusts X-Forwarded-For only because this app sits behind a reverse
    // proxy in any real deployment; falls back to the raw socket address
    // for local/dev use where there is no proxy in front.
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('MERCHANT')")
    public List<PaymentHistoryItem> getMyPayments(
            @RequestParam(defaultValue = "CUSTOMER") String ownerType,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        Long ownerId = "MERCHANT".equalsIgnoreCase(ownerType)
                ? merchantRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No merchant profile for this account"))
                .getId()
                : customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No customer profile for this account"))
                .getId();

        return paymentRepository.findForOwner(ownerId, ownerType.toUpperCase())
                .stream()
                .map(p -> new PaymentHistoryItem(
                        p.getId(),
                        (p.getSenderOwnerId().equals(ownerId) && p.getSenderOwnerType().equalsIgnoreCase(ownerType)) ? "SENT" : "RECEIVED",
                        p.getAmount(),
                        p.getCurrency(),
                        p.getStatus(),
                        p.getCreatedAt()
                ))
                .toList();
    }
}
