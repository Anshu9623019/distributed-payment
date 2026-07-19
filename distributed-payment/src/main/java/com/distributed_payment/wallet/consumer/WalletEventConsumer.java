package com.distributed_payment.wallet.consumer;

import com.distributed_payment.outbox.entity.OutboxEvent;
import com.distributed_payment.outbox.entity.OutboxStatus;
import com.distributed_payment.outbox.repository.OutboxEventRepository;
import com.distributed_payment.payment.event.PaymentFailedEvent;
import com.distributed_payment.payment.event.PaymentInitiatedEvent;
import com.distributed_payment.wallet.event.WalletDebitedEvent;
import com.distributed_payment.wallet.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletEventConsumer {

    private static final String TOPIC_DEBITED = "wallet-debited";
    private static final String TOPIC_FAILED = "payment-failed";

    private final WalletService walletService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-initiated", groupId = "wallet-service-group")
    @Transactional
    public void handlePaymentInitiated(String payload) throws JsonProcessingException {
        PaymentInitiatedEvent event = objectMapper.readValue(payload, PaymentInitiatedEvent.class);
        log.info("Received PaymentInitiatedEvent for paymentId={}", event.paymentId());

        try {
            // debit() is @Transactional and will join this transaction (REQUIRED
            // propagation), so the debit and the outbox write below either both
            // commit or both roll back together — no dual-write gap here either.
            walletService.debit(event.senderId(), event.senderType(), event.amount(), event.paymentId());

            WalletDebitedEvent debitedEvent = new WalletDebitedEvent(
                    event.paymentId(), event.senderId(), event.senderType(),
                    event.receiverId(), event.receiverType(), event.amount());

            saveOutboxEvent(event.paymentId(), TOPIC_DEBITED, "WalletDebitedEvent", debitedEvent);
            log.info("Sender debited, queued WalletDebitedEvent for paymentId={}", event.paymentId());

        } catch (Exception ex) {
            log.warn("Debit failed for paymentId={}: {}", event.paymentId(), ex.getMessage());

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    event.paymentId(), event.senderId(), event.senderType(), event.amount(), ex.getMessage());

            saveOutboxEvent(event.paymentId(), TOPIC_FAILED, "PaymentFailedEvent", failedEvent);
        }
    }

    private void saveOutboxEvent(String aggregateId, String topic, String eventType, Object payload) throws JsonProcessingException {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .topic(topic)
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PENDING)
                .build();
        outboxEventRepository.save(outboxEvent);
    }
}