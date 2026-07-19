package com.distributed_payment.payment.consumer;

import com.distributed_payment.exception.ResourceNotFoundException;
import com.distributed_payment.payment.entity.Payment;
import com.distributed_payment.payment.entity.PaymentStatus;
import com.distributed_payment.payment.event.PaymentFailedEvent;
import com.distributed_payment.payment.repo.PaymentRepository;
import com.distributed_payment.wallet.event.WalletDebitedEvent;
import com.distributed_payment.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final WalletService walletService;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wallet-debited", groupId = "payment-service-group")
    @Transactional
    public void handleWalletDebited(String payload) throws Exception {
        WalletDebitedEvent event = objectMapper.readValue(payload, WalletDebitedEvent.class);
        log.info("Received WalletDebitedEvent for paymentId={}", event.paymentId());

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + event.paymentId()));

        try {
            payment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.saveAndFlush(payment);

            walletService.credit(event.receiverId(), event.receiverType(), event.amount(), event.paymentId());

            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            log.info("Saga complete, paymentId={} SUCCESS", event.paymentId());

        } catch (Exception ex) {
            log.error("Receiver credit failed for paymentId={}, running compensation. Cause: {}",
                    event.paymentId(), ex.getMessage());

            try {
                walletService.credit(event.senderId(), event.senderType(), event.amount(), event.paymentId() + "_COMPENSATION");

                payment.setStatus(PaymentStatus.REVERSED);
                payment.setErrorMessage("Receiver credit failed, auto-reversed: " + ex.getMessage());
                paymentRepository.save(payment);
                log.warn("Compensation succeeded, paymentId={} REVERSED", event.paymentId());
            } catch (Exception compEx) {
                log.error("Compensation FAILED for paymentId={}. Manual reconciliation required. Cause: {}",
                        event.paymentId(), compEx.getMessage());
                throw compEx; // let it hit the consumer's error handler / DLQ
            }
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "payment-service-group")
    @Transactional
    public void handlePaymentFailed(String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        log.warn("Received PaymentFailedEvent for paymentId={}, reason={}", event.paymentId(), event.failureReason());

        paymentRepository.findById(event.paymentId()).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(event.failureReason());
            paymentRepository.save(payment);
        }, () -> log.error("Payment not found for failure event, paymentId={}", event.paymentId()));
    }
}
