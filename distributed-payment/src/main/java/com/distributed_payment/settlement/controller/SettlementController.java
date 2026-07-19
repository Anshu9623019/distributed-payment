package com.distributed_payment.settlement.controller;

import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.security.User;
import com.distributed_payment.settlement.entity.Settlement;
import com.distributed_payment.settlement.repository.SettlementRepository;
import com.distributed_payment.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementRepository settlementRepository;
    private final MerchantRepository merchantRepository;

    // A merchant checking their own payout history.
    @GetMapping("/me")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('CUSTOMER')")
    public List<Settlement> getMySettlements(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Long merchantId = merchantRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No merchant profile for this account"))
                .getId();
        return settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    // Ops visibility across all merchants.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Settlement> getAllSettlements() {
        return settlementRepository.findAllByOrderByCreatedAtDesc();
    }

    // Runs the scheduled job on demand — useful for demos/testing without
    // waiting for the 02:00 cron, and for ops to force a run after an
    // incident.
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public void triggerSettlementRun() {
        settlementService.runDailySettlement();
    }

    // Settle one specific merchant on demand (e.g. they requested an early payout).
    @PostMapping("/merchants/{merchantId}/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Settlement triggerSettlementForMerchant(@PathVariable Long merchantId) {
        return settlementService.settleMerchant(merchantId);
    }
}
