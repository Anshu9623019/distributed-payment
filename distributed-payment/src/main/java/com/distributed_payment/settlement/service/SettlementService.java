package com.distributed_payment.settlement.service;

import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.merchant.entity.Merchant;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.settlement.entity.Settlement;
import com.distributed_payment.settlement.entity.SettlementStatus;
import com.distributed_payment.settlement.repository.SettlementRepository;
import com.distributed_payment.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final MerchantRepository merchantRepository;
    private final WalletService walletService;
    private final SettlementRepository settlementRepository;
    private final BankTransferService bankTransferService;

    // Runs once a day at 02:00. Real systems offer merchants a choice of
    // daily/hourly cadence (per the original design doc); this keeps one
    // schedule for simplicity and exposes a manual trigger for the rest.
    @Scheduled(cron = "0 0 2 * * *")
    public void runDailySettlement() {
        List<Merchant> activeMerchants = merchantRepository.findByStatus("ACTIVE");
        log.info("Starting daily settlement run for {} active merchants", activeMerchants.size());

        for (Merchant merchant : activeMerchants) {
            try {
                settleMerchant(merchant.getId());
            } catch (Exception ex) {
                // One merchant's failure should never abort the batch for everyone else.
                log.error("Settlement run failed unexpectedly for merchantId={}: {}", merchant.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Settles a single merchant's current wallet balance. Split into two
     * transactional steps rather than one, because the sweep (money leaving
     * the ledger) and the bank call (an external, non-transactional side
     * effect) can't share a DB transaction — if they did, a slow bank API
     * would hold the DB lock on the wallet row for the whole call.
     */
    public Settlement settleMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));

        Settlement settlement = beginSettlement(merchant);
        if (settlement.getStatus() == SettlementStatus.COMPLETED && settlement.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return settlement; // nothing to settle, no-op
        }

        BankTransferResult result = bankTransferService.transfer(
                merchant.getSettlementAccount(), settlement.getAmount(), settlement.getCurrency());

        return result.success()
                ? completeSettlement(settlement, result.bankReference())
                : failAndRefund(settlement, merchant, result.failureReason());
    }

    @Transactional
    protected Settlement beginSettlement(Merchant merchant) {
        // referenceId ties this specific sweep to its ledger entry, so a
        // retried/duplicate settlement attempt for the same period can't
        // double-debit (mirrors the idempotency pattern used elsewhere).
        String referenceId = "SETTLEMENT_" + merchant.getId() + "_" + System.currentTimeMillis();
        BigDecimal amountSwept = walletService.debitFullBalance(merchant.getId(), "MERCHANT", referenceId);

        Settlement settlement = Settlement.builder()
                .merchantId(merchant.getId())
                .amount(amountSwept)
                .currency("USD")
                .status(amountSwept.compareTo(BigDecimal.ZERO) == 0 ? SettlementStatus.COMPLETED : SettlementStatus.PROCESSING)
                .build();

        return settlementRepository.save(settlement);
    }

    @Transactional
    protected Settlement completeSettlement(Settlement settlement, String bankReference) {
        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setBankReference(bankReference);
        settlement.setCompletedAt(LocalDateTime.now());
        log.info("Settlement id={} COMPLETED for merchantId={}, amount={}, ref={}",
                settlement.getId(), settlement.getMerchantId(), settlement.getAmount(), bankReference);
        return settlementRepository.save(settlement);
    }

    /**
     * Compensating transaction: the bank rail rejected the payout, so the
     * money that was already swept out of the merchant's wallet is credited
     * back. Without this, a failed bank transfer would silently vanish the
     * merchant's balance — exactly the kind of gap the Saga pattern exists
     * to close elsewhere in this codebase.
     */
    @Transactional
    protected Settlement failAndRefund(Settlement settlement, Merchant merchant, String reason) {
        walletService.credit(merchant.getId(), "MERCHANT", settlement.getAmount(),
                "SETTLEMENT_" + settlement.getId() + "_REFUND");

        settlement.setStatus(SettlementStatus.FAILED);
        settlement.setFailureReason(reason);
        log.warn("Settlement id={} FAILED for merchantId={}, refunded {} back to wallet. Reason: {}",
                settlement.getId(), settlement.getMerchantId(), settlement.getAmount(), reason);
        return settlementRepository.save(settlement);
    }
}
