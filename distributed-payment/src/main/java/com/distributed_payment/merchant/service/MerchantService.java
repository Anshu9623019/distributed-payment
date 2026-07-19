package com.distributed_payment.merchant.service;

import com.distributed_payment.exception.DuplicateResourceException;
import com.distributed_payment.merchant.entity.Merchant;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private static final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Merchant onboardMerchant(Long userId, String businessName, String webhookUrl, String settlementAccount) {
        if (merchantRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException("This account is already onboarded as a merchant");
        }

        String rawApiKey = "pk_" + UUID.randomUUID().toString().replace("-", "");

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String rawApiSecret = "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        Merchant merchant = Merchant.builder()
                .userId(userId)
                .businessName(businessName)
                .apiKey(rawApiKey)
                .apiSecret(passwordEncoder.encode(rawApiSecret))
                .webhookUrl(webhookUrl)
                .settlementAccount(settlementAccount)
                .status("ACTIVE")
                .build();

        Merchant saved = merchantRepository.save(merchant);
        walletService.ensureWalletExists(saved.getId(), "MERCHANT", "USD");
        log.info("Onboarded merchantId={} for userId={}", saved.getId(), userId);

        // Return the raw (unhashed) secret ONCE, at creation time only. It is never
        // retrievable again — the DB only ever stores the hash.
        return Merchant.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .businessName(saved.getBusinessName())
                .apiKey(rawApiKey)
                .apiSecret(rawApiSecret)
                .webhookUrl(saved.getWebhookUrl())
                .settlementAccount(saved.getSettlementAccount())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}