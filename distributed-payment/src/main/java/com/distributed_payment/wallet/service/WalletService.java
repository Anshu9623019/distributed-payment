package com.distributed_payment.wallet.service;

import com.distributed_payment.exception.InsufficientBalanceException;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.ledger.entity.EntryType;
import com.distributed_payment.ledger.repository.LedgerEntryRepository;
import com.distributed_payment.ledger.service.LedgerService;
import com.distributed_payment.wallet.entity.Wallet;
import com.distributed_payment.wallet.repo.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private static final String CACHE_NAME = "wallet-balances";

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final CacheManager cacheManager;

    @Cacheable(value = CACHE_NAME, key = "#walletId")
    @Transactional(readOnly = true)
    public Wallet getWallet(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }

    public Wallet getWalletByOwner(Long ownerId, String ownerType) {
        return walletRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for " + ownerType + " id " + ownerId));
    }

    /**
     * Called once, right after a Customer or Merchant profile is created, so
     * every account has exactly one wallet from day one. Safe to call more
     * than once (idempotent) since it checks for an existing wallet first.
     */
    @Transactional
    public Wallet ensureWalletExists(Long ownerId, String ownerType, String currency) {
        return walletRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder()
                                .ownerId(ownerId)
                                .ownerType(ownerType)
                                .balance(BigDecimal.ZERO)
                                .currency(currency)
                                .build()
                ));
    }

    @Transactional
    public void credit(Long ownerId, String ownerType, BigDecimal amount, String paymentId) {
        if (paymentId != null && ledgerEntryRepository.existsByPaymentIdAndEntryType(paymentId, EntryType.CREDIT)) {
            log.warn("Credit for paymentId={} already recorded, skipping (idempotent replay)", paymentId);
            return;
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerTypeForUpdate(ownerId, ownerType)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for " + ownerType + " id " + ownerId));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        ledgerService.recordEntry(wallet.getId(), paymentId, EntryType.CREDIT, amount);

        evictCache(wallet.getId());
        log.info("Credited {} to walletId={}, new balance={}", amount, wallet.getId(), wallet.getBalance());
    }

    @Transactional
    public void debit(Long ownerId, String ownerType, BigDecimal amount, String paymentId) {
        if (paymentId != null && ledgerEntryRepository.existsByPaymentIdAndEntryType(paymentId, EntryType.DEBIT)) {
            log.warn("Debit for paymentId={} already recorded, skipping (idempotent replay)", paymentId);
            return;
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerTypeForUpdate(ownerId, ownerType)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for " + ownerType + " id " + ownerId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in wallet " + wallet.getId() + ": have " + wallet.getBalance() + ", need " + amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
        ledgerService.recordEntry(wallet.getId(), paymentId, EntryType.DEBIT, amount);

        evictCache(wallet.getId());
        log.info("Debited {} from walletId={}, new balance={}", amount, wallet.getId(), wallet.getBalance());
    }

    /**
     * Sweeps the entire current balance to zero in one locked read+write —
     * used by settlement, where "amount to pay out" must be exactly whatever
     * the balance is at the moment of the sweep. Reading the balance first
     * and debiting that amount as two separate calls would leave a race
     * window where a concurrent credit lands in between, and the settlement
     * amount would then be stale by the time it's paid out.
     */
    @Transactional
    public BigDecimal debitFullBalance(Long ownerId, String ownerType, String referenceId) {
        Wallet wallet = walletRepository.findByOwnerIdAndOwnerTypeForUpdate(ownerId, ownerType)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for " + ownerType + " id " + ownerId));

        BigDecimal balance = wallet.getBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        wallet.setBalance(BigDecimal.ZERO);
        walletRepository.save(wallet);
        ledgerService.recordEntry(wallet.getId(), referenceId, EntryType.DEBIT, balance);

        evictCache(wallet.getId());
        log.info("Swept full balance {} from walletId={} for settlement", balance, wallet.getId());
        return balance;
    }

    // Evicting by the actual wallet.getId() here (instead of a @CacheEvict annotation
    // keyed on a method parameter that doesn't exist, e.g. #walletId on a method whose
    // params are ownerId/ownerType) is what makes this reliable.
    private void evictCache(Long walletId) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(walletId);
        }
    }
}