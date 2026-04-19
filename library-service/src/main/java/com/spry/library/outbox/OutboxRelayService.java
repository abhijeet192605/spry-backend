package com.spry.library.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private static final String TOPIC = "book.status.changed";
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("outbox.pending.count", outboxRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Number of outbox events waiting to be published")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        var pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        for (var event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                event.markFailed();
            }
        }

        log.debug("Outbox relay processed {} events", pending.size());
    }
}
