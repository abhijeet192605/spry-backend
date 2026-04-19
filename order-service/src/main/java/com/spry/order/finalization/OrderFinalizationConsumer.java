package com.spry.order.finalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.order.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class OrderFinalizationConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final Timer finalizationTimer;

    public OrderFinalizationConsumer(OrderService orderService,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.finalizationTimer = Timer.builder("orders.finalization.duration")
                .description("Time to finalize an order after Kafka event received")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "order.created", groupId = "order-finalization")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            log.info("Finalizing order: id={}, partition={}, offset={}", orderId, record.partition(), record.offset());

            finalizationTimer.record(() -> orderService.finalizeOrder(orderId));

        } catch (Exception e) {
            log.error("Failed to process finalization event at offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("Order finalization failed", e);
        }
    }
}
