package com.spry.order.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.order.OrderRepository;
import com.spry.order.outbox.OutboxEvent;
import com.spry.order.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Component
public class StuckOrderRecoveryScheduler {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Counter recoveredCounter;

    public StuckOrderRecoveryScheduler(OrderRepository orderRepository,
                                       OutboxEventRepository outboxRepository,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.recoveredCounter = meterRegistry.counter("orders.pending.stale.recovered");
    }

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void recover() {
        var threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        var stuck = orderRepository.findStuckPendingOrders(threshold);

        if (!stuck.isEmpty()) {
            log.warn("Found {} stuck PENDING orders older than 5 minutes — re-queueing", stuck.size());
        }

        for (var order : stuck) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "orderId", order.getId().toString(),
                        "customerId", order.getCustomer().getId().toString()
                ));
                outboxRepository.save(OutboxEvent.of(order.getId(), "OrderCreated", payload));
                recoveredCounter.increment();
                log.info("Re-queued stuck order: id={}", order.getId());
            } catch (JsonProcessingException e) {
                log.error("Failed to re-queue stuck order {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
