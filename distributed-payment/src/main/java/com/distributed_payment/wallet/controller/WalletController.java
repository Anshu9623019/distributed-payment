package com.distributed_payment.wallet.controller;

import com.distributed_payment.customer.repository.CustomerRepository;
import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.payment.service.StripeService;
import com.distributed_payment.security.User;
import com.distributed_payment.wallet.dto.TopUpRequest;
import com.distributed_payment.wallet.dto.TopUpResponse;
import com.distributed_payment.wallet.dto.WalletResponse;
import com.distributed_payment.wallet.entity.Wallet;
import com.distributed_payment.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * "me" endpoints resolve the caller's own wallet from their JWT identity —
 * a customer can never read or top up anyone else's wallet by guessing an ID.
 * ownerType picks which of the caller's two possible wallets to act on,
 * since the same logged-in user may be both a Customer and a Merchant.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CustomerRepository customerRepository;
    private final MerchantRepository merchantRepository;
    private final StripeService stripeService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('MERCHANT')")
    public WalletResponse getMyWallet(
            @RequestParam(defaultValue = "CUSTOMER") String ownerType,
            Authentication authentication) {

        Wallet wallet = walletService.getWalletByOwner(resolveOwnerId(authentication, ownerType), ownerType);
        return new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(), wallet.getBalance(), wallet.getCurrency());
    }

    @PostMapping("/topup")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('MERCHANT')")
    public TopUpResponse topUp(
            @RequestParam(defaultValue = "CUSTOMER") String ownerType,
            @Valid @RequestBody TopUpRequest request,
            Authentication authentication) throws Exception {

        Wallet wallet = walletService.getWalletByOwner(resolveOwnerId(authentication, ownerType), ownerType);
        String url = stripeService.createTopUpSession(wallet.getId(), request.amount(), wallet.getCurrency());
        return new TopUpResponse(url);
    }

    /**
     * DEV/TEST ONLY — credits the caller's own wallet directly, bypassing
     * Stripe entirely. This exists so the payment/fraud/settlement flow can
     * be exercised end-to-end without real Stripe API keys. It's scoped to
     * the caller's own wallet (not an arbitrary target), which limits the
     * blast radius, but it should still be removed or feature-flagged off
     * before any real deployment — do not ship this to production.
     */
    @PostMapping("/test-credit")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('MERCHANT')")
    public WalletResponse testCredit(
            @RequestParam(defaultValue = "CUSTOMER") String ownerType,
            @Valid @RequestBody TopUpRequest request,
            Authentication authentication) {

        Long ownerId = resolveOwnerId(authentication, ownerType);
        walletService.credit(ownerId, ownerType.toUpperCase(), request.amount(), "TEST_CREDIT_" + System.currentTimeMillis());

        Wallet wallet = walletService.getWalletByOwner(ownerId, ownerType);
        return new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(), wallet.getBalance(), wallet.getCurrency());
    }

    private Long resolveOwnerId(Authentication authentication, String ownerType) {
        User user = (User) authentication.getPrincipal();

        if ("MERCHANT".equalsIgnoreCase(ownerType)) {
            return merchantRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("No merchant profile for this account"))
                    .getId();
        }

        return customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No customer profile for this account"))
                .getId();
    }
}