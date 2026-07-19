package com.distributed_payment.outbox.publisher;

import com.distributed_payment.outbox.entity.OutboxEvent;
import com.distributed_payment.outbox.entity.OutboxStatus;
import com.distributed_payment.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;

    // CHANGE HERE: Change Object to String. Spring Boot automatically configures
    // a default String template bean if you declare the type explicitly.
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                // Now event.getPayload() bypasses JsonSerializer and writes raw text to the wire
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                log.info("Published outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event id={} exceeded max retries, marking FAILED: {}", event.getId(), ex.getMessage());
                } else {
                    log.warn("Outbox event id={} publish attempt {} failed: {}", event.getId(), event.getRetryCount(), ex.getMessage());
                }
            }
            outboxEventRepository.save(event);
        }
    }
}