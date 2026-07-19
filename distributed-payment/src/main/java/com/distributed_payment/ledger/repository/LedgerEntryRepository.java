package com.distributed_payment.ledger.repository;

import com.distributed_payment.ledger.entity.EntryType;
import com.distributed_payment.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByPaymentIdAndEntryType(String paymentId, EntryType entryType);
}
