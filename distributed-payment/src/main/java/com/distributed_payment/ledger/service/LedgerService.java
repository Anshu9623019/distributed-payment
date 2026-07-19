package com.distributed_payment.ledger.service;

import com.distributed_payment.ledger.entity.EntryType;
import com.distributed_payment.ledger.entity.LedgerEntry;
import com.distributed_payment.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    // MANDATORY: this must run inside the caller's transaction. A ledger entry
    // written outside the same transaction as its balance change would let the
    // two drift apart under a partial failure.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEntry(Long walletId, String paymentId, EntryType type, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .walletId(walletId)
                .paymentId(paymentId)
                .entryType(type)
                .amount(amount)
                .build();

        ledgerEntryRepository.save(entry);
        log.debug("Recorded {} of {} for walletId={}", type, amount, walletId);
    }
}
