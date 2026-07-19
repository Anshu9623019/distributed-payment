package com.distributed_payment.merchant.consumer;

import com.distributed_payment.merchant.repo.MerchantRepository;
import com.distributed_payment.wallet.event.WalletDebitedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantNotificationConsumer {

    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @KafkaListener(topics = "wallet-debited", groupId = "merchant-notification-group")
    public void notifyMerchantOnSuccess(String payload) throws Exception {
        WalletDebitedEvent event = objectMapper.readValue(payload, WalletDebitedEvent.class);

        if (!"MERCHANT".equalsIgnoreCase(event.receiverType())) {
            return;
        }

        merchantRepository.findById(event.receiverId()).ifPresentOrElse(merchant -> {
            if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) {
                return;
            }

            Map<String, Object> webhookPayload = Map.of(
                    "event", "payment.captured",
                    "paymentId", event.paymentId(),
                    "amount", event.amount(),
                    "status", "SUCCESS"
            );

            try {
                restTemplate.postForEntity(merchant.getWebhookUrl(), webhookPayload, String.class);
                log.info("Webhook delivered to merchantId={}", merchant.getId());
            } catch (Exception ex) {
                // Fire-and-forget for now. In production this would go through the
                // outbox + retry/DLQ path too instead of a best-effort HTTP call.
                log.error("Webhook delivery failed for merchantId={} url={}: {}",
                        merchant.getId(), merchant.getWebhookUrl(), ex.getMessage());
            }
        }, () -> log.error("Merchant not found for receiverId={}", event.receiverId()));
    }
}
