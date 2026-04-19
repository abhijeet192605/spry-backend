package com.spry.library.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    // Field so the gauge test can query it after setUp() registers the metric
    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxRelayService = new OutboxRelayService(outboxRepository, kafkaTemplate, meterRegistry);
        outboxRelayService.registerMetrics();
    }

    @Test
    void relay_doesNothingWhenNoPendingEvents() {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        outboxRelayService.relay();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void relay_publishesPendingEventToKafka() {
        var event = pendingEvent();
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelayService.relay();

        verify(kafkaTemplate).send(
                eq("book.status.changed"),
                eq(event.getAggregateId().toString()),
                eq(event.getPayload()));
    }

    @Test
    void relay_marksEventAsPublished_afterSuccessfulSend() {
        var event = pendingEvent();
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void relay_marksEventAsFailed_whenKafkaSendThrows() {
        var event = pendingEvent();
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));

        var failedFuture = new CompletableFuture<SendResult<String, String>>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        outboxRelayService.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void relay_continuesProcessingRemainingEvents_afterOneFails() {
        var failingEvent = pendingEvent();
        var successEvent = pendingEvent();

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(failingEvent, successEvent));

        var failedFuture = new CompletableFuture<SendResult<String, String>>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));

        when(kafkaTemplate.send(anyString(), eq(failingEvent.getAggregateId().toString()), anyString()))
                .thenReturn(failedFuture);
        when(kafkaTemplate.send(anyString(), eq(successEvent.getAggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelayService.relay();

        assertThat(failingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(successEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void relay_publishesMultiplePendingEvents() {
        var event1 = pendingEvent();
        var event2 = pendingEvent();
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelayService.relay();

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        assertThat(event1.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event2.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void registerMetrics_gaugeReportsCorrectPendingCount() {
        when(outboxRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);

        // Querying the gauge value triggers the supplier function registered in registerMetrics()
        double value = meterRegistry.get("outbox.pending.count").gauge().value();

        assertThat(value).isEqualTo(5.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OutboxEvent pendingEvent() {
        return OutboxEvent.of(
                UUID.randomUUID(),
                "BookStatusChanged",
                "{\"bookId\":\"" + UUID.randomUUID() + "\",\"bookTitle\":\"Test\",\"newStatus\":\"AVAILABLE\"}"
        );
    }
}
