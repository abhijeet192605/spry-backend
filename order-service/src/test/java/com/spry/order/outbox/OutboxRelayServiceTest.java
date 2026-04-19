package com.spry.order.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new OutboxRelayService(outboxRepository, kafkaTemplate, new SimpleMeterRegistry());
    }

    @Test
    void relay_doesNothing_whenNoPendingEvents() {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        relayService.relay();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void relay_publishesPendingEvent_toOrderCreatedTopic() throws Exception {
        var event = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"abc\"}");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(successFuture());

        relayService.relay();

        verify(kafkaTemplate).send(eq("order.created"), eq(event.getAggregateId().toString()),
                eq(event.getPayload()));
    }

    @Test
    void relay_marksEventAsPublished_afterSuccessfulKafkaSend() {
        var event = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"abc\"}");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(successFuture());

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void relay_marksEventAsFailed_whenKafkaThrows() {
        var event = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"abc\"}");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture(new RuntimeException("broker unavailable")));

        relayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void relay_processesAllEventsInBatch() {
        var event1 = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"1\"}");
        var event2 = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"2\"}");
        var event3 = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"3\"}");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event1, event2, event3));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(successFuture());

        relayService.relay();

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString());
        assertThat(event1.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event2.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event3.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void relay_continuesProcessing_whenOneEventFails() {
        var failingEvent = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"fail\"}");
        var successEvent = pendingEvent(UUID.randomUUID(), "{\"orderId\":\"ok\"}");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(failingEvent, successEvent));
        when(kafkaTemplate.send(anyString(), eq(failingEvent.getAggregateId().toString()), anyString()))
                .thenReturn(failedFuture(new RuntimeException("broker error")));
        when(kafkaTemplate.send(anyString(), eq(successEvent.getAggregateId().toString()), anyString()))
                .thenReturn(successFuture());

        relayService.relay();

        assertThat(failingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(successEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OutboxEvent pendingEvent(UUID aggregateId, String payload) {
        return OutboxEvent.of(aggregateId, "OrderCreated", payload);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> successFuture() {
        return CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, String>> failedFuture(Throwable cause) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }
}
