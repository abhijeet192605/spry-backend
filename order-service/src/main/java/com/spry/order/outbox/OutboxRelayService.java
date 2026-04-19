package com.spry.order.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxRelayService {

    private static final String TOPIC = "order.created";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayService(OutboxEventRepository outboxRepository,
                              KafkaTemplate<String, String> kafkaTemplate,
                              MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;

        meterRegistry.gauge("orders.outbox.pending.count", outboxRepository,
                repo -> repo.countByStatus(OutboxStatus.PENDING));
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        var pending = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 50));

        for (var event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                event.markFailed();
            }
        }
    }
}
