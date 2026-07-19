package com.distributed_payment.payment.controller;

import com.distributed_payment.wallet.service.WalletService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final WalletService walletService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Stripe webhook parsing error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

            if (deserializer.getObject().isPresent()) {
                Session session = (Session) deserializer.getObject().get();
                String walletIdStr = session.getMetadata().get("wallet_id");

                if (walletIdStr != null) {
                    Long walletId = Long.parseLong(walletIdStr);
                    BigDecimal amount = BigDecimal.valueOf(session.getAmountTotal()).divide(new BigDecimal("100"));
                    String externalPaymentId = "STRIPE_" + session.getId();

                    try {
                        // credit() is idempotent on paymentId via the ledger check,
                        // so Stripe's at-least-once webhook retries are safe here.
                        var wallet = walletService.getWallet(walletId); // 404s if wallet doesn't exist
                        walletService.credit(wallet.getOwnerId(), wallet.getOwnerType(), amount, externalPaymentId);
                        log.info("Top-up successful for walletId={}", walletId);
                    } catch (Exception e) {
                        log.error("Failed to credit wallet during top-up, walletId={}: {}", walletId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
