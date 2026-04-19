package com.spry.order.finalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.order.OrderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderFinalizationConsumerTest {

    @Mock OrderService orderService;

    OrderFinalizationConsumer consumer;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new OrderFinalizationConsumer(orderService, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void consume_callsFinalizeWithCorrectOrderId() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId.toString(),
                "customerId", UUID.randomUUID().toString()
        ));
        var record = new ConsumerRecord<String, String>("order.created", 0, 10L, orderId.toString(), payload);

        consumer.consume(record);

        verify(orderService).finalizeOrder(orderId);
    }

    @Test
    void consume_throwsRuntimeException_onMalformedPayload() {
        var record = new ConsumerRecord<String, String>("order.created", 0, 11L, "key", "not-json");

        assertThrows(RuntimeException.class, () -> consumer.consume(record));
        verifyNoInteractions(orderService);
    }

    @Test
    void consume_throwsRuntimeException_onMissingOrderIdField() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("customerId", UUID.randomUUID().toString()));
        var record = new ConsumerRecord<String, String>("order.created", 0, 12L, "key", payload);

        assertThrows(RuntimeException.class, () -> consumer.consume(record));
        verifyNoInteractions(orderService);
    }

    @Test
    void consume_propagatesException_whenFinalizationFails() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId.toString(),
                "customerId", UUID.randomUUID().toString()
        ));
        var record = new ConsumerRecord<String, String>("order.created", 0, 13L, orderId.toString(), payload);
        doThrow(new RuntimeException("DB error")).when(orderService).finalizeOrder(any());

        assertThrows(RuntimeException.class, () -> consumer.consume(record));
        verify(orderService).finalizeOrder(orderId);
    }
}
