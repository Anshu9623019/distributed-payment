package com.distributed_payment.merchant.controller;

import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.merchant.dto.MerchantOnboardRequest;
import com.distributed_payment.merchant.dto.MerchantSummaryResponse;
import com.distributed_payment.merchant.entity.Merchant;
import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.merchant.service.MerchantService;
import com.distributed_payment.security.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantRepository merchantRepository;

    @PostMapping("/onboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Merchant> onboardMerchant(
            @Valid @RequestBody MerchantOnboardRequest request,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        Merchant newMerchant = merchantService.onboardMerchant(
                user.getId(),
                request.businessName(),
                request.webhookUrl(),
                request.settlementAccount()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(newMerchant);
    }

    // Used by the dashboard to check "am I already a merchant" and render the
    // API key / webhook status without ever re-exposing the hashed secret.
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('MERCHANT')")
    public MerchantSummaryResponse getMyMerchantProfile(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Merchant merchant = merchantRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No merchant profile for this account"));

        return new MerchantSummaryResponse(
                merchant.getId(), merchant.getBusinessName(), merchant.getApiKey(),
                merchant.getWebhookUrl(), merchant.getSettlementAccount(), merchant.getStatus());
    }
}