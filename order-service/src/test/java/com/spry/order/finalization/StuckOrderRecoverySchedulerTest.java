package com.spry.order.finalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.customer.Customer;
import com.spry.order.order.Order;
import com.spry.order.order.OrderRepository;
import com.spry.order.order.OrderStatus;
import com.spry.order.outbox.OutboxEvent;
import com.spry.order.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StuckOrderRecoverySchedulerTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;

    StuckOrderRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StuckOrderRecoveryScheduler(
                orderRepository, outboxRepository, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void recover_doesNothing_whenNoStuckOrders() {
        when(orderRepository.findStuckPendingOrders(any())).thenReturn(List.of());

        scheduler.recover();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void recover_createsOutboxEvent_forEachStuckOrder() {
        var order1 = pendingOrderWithCustomer();
        var order2 = pendingOrderWithCustomer();
        when(orderRepository.findStuckPendingOrders(any())).thenReturn(List.of(order1, order2));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.recover();

        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void recover_outboxEventContainsOrderAndCustomerIds() {
        var order = pendingOrderWithCustomer();
        when(orderRepository.findStuckPendingOrders(any())).thenReturn(List.of(order));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.recover();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo("OrderCreated");
        assertThat(saved.getAggregateId()).isEqualTo(order.getId());
        assertThat(saved.getPayload()).contains(order.getId().toString());
        assertThat(saved.getPayload()).contains(order.getCustomer().getId().toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order pendingOrderWithCustomer() {
        var customer = new Customer();
        try {
            var f = Customer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(customer, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        customer.setName("Test Customer");
        customer.setEmail("test@example.com");

        var order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(Instant.now().minusSeconds(600));
        order.setStatus(OrderStatus.PENDING);
        return order;
    }
}
