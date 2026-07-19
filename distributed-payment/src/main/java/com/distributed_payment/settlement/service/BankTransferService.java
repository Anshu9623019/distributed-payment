package com.distributed_payment.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a real bank rail integration (ACH / NEFT / RTGS / a payout
 * API like Stripe Connect or Wise Platform). Kept as its own service so the
 * mock is swappable for a real client without touching SettlementService's
 * orchestration or compensation logic.
 */
@Service
@Slf4j
public class BankTransferService {

    // Small simulated failure rate so the compensation path in
    // SettlementService actually gets exercised, not just theorized about.
    private static final int SIMULATED_FAILURE_PERCENT = 5;

    public BankTransferResult transfer(String settlementAccount, BigDecimal amount, String currency) {
        if (settlementAccount == null || settlementAccount.isBlank()) {
            return BankTransferResult.failure("No settlement account on file for this merchant");
        }

        if (ThreadLocalRandom.current().nextInt(100) < SIMULATED_FAILURE_PERCENT) {
            log.warn("Simulated bank rail failure for account {}", settlementAccount);
            return BankTransferResult.failure("Bank rail rejected the transfer (simulated timeout)");
        }

        String reference = "BANK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("Simulated payout of {} {} to account {} -> reference {}", amount, currency, settlementAccount, reference);
        return BankTransferResult.success(reference);
    }
}
