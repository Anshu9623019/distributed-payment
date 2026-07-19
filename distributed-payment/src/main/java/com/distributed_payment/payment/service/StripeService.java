package com.distributed_payment.payment.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripeService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.checkout.success-url}")
    private String successUrl;

    @Value("${stripe.checkout.cancel-url}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public String createTopUpSession(Long walletId, BigDecimal amount, String currency) throws Exception {
        long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(currency.toLowerCase())
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Wallet Top-Up")
                                                                .build())
                                                .build())
                                .build())
                // Attach the internal wallet ID so the webhook knows who to credit.
                .putMetadata("wallet_id", String.valueOf(walletId))
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }
}
