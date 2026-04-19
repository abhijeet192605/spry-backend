package com.spry.order.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.customer.Customer;
import com.spry.order.customer.CustomerService;
import com.spry.order.order.dto.CreateOrderRequest;
import com.spry.order.order.dto.OrderItemRequest;
import com.spry.order.order.dto.OrderMapper;
import com.spry.order.order.dto.OrderMapperImpl;
import com.spry.order.order.dto.UpdateStatusRequest;
import com.spry.order.order.exception.ConcurrentOrderModificationException;
import com.spry.order.order.exception.InvalidStatusTransitionException;
import com.spry.order.order.exception.OrderNotDeletableException;
import com.spry.order.order.exception.OrderNotFoundException;
import com.spry.order.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock CustomerService customerService;

    OrderService orderService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OrderMapper mapper = new OrderMapperImpl();
        orderService = new OrderService(orderRepository, outboxRepository, customerService,
                mapper, new ObjectMapper(), new SimpleMeterRegistry());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesOrderAndPublishesOutboxEvent() {
        var customer = customerWithId(customerId);
        when(customerService.findCustomer(customerId)).thenReturn(customer);
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.create(createOrderRequest(customerId));

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalValue()).isNull();
        assertThat(response.items()).hasSize(1);
        verify(orderRepository).saveAndFlush(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void create_throwsNotFound_whenCustomerDoesNotExist() {
        when(customerService.findCustomer(customerId)).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> orderService.create(createOrderRequest(customerId)))
                .isInstanceOf(RuntimeException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_returnsOrder_whenFound() {
        var order = orderWithStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        var response = orderService.getById(order.getId());

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void getById_throwsNotFound_whenOrderMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_confirmed_toShipped_succeeds() {
        var order = orderWithStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.SHIPPED, 0L), actorId);

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateStatus_pending_toCancelled_succeeds() {
        var order = orderWithStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.CANCELLED, 0L), actorId);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateStatus_confirmed_toCancelled_succeeds() {
        var order = orderWithStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.CANCELLED, 0L), actorId);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateStatus_pending_toConfirmed_isRejected() {
        var order = orderWithStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.CONFIRMED, 0L), actorId))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateStatus_shipped_toAnything_isRejected() {
        var order = orderWithStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.CANCELLED, 0L), actorId))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateStatus_throwsConflict_whenVersionMismatch() {
        var order = orderWithStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.SHIPPED, 99L), actorId))
                .isInstanceOf(ConcurrentOrderModificationException.class);
    }

    @Test
    void updateStatus_throwsConflict_onOptimisticLockException() {
        var order = orderWithStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, order.getId()));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(),
                new UpdateStatusRequest(OrderStatus.SHIPPED, 0L), actorId))
                .isInstanceOf(ConcurrentOrderModificationException.class);
    }

    // ── finalizeOrder ─────────────────────────────────────────────────────────

    @Test
    void finalizeOrder_setsConfirmedAndTotal_whenPending() {
        var order = orderWithStatus(OrderStatus.PENDING);
        var item = new OrderItem();
        item.setUnitPrice(new BigDecimal("45.99"));
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("91.98"));
        item.setOrder(order);
        order.getItems().add(item);

        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.finalizeOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTotalValue()).isEqualByComparingTo("91.98");
    }

    @Test
    void finalizeOrder_isIdempotent_whenAlreadyConfirmed() {
        var order = orderWithStatus(OrderStatus.CONFIRMED);
        order.setTotalValue(new BigDecimal("50.00"));
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.finalizeOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTotalValue()).isEqualByComparingTo("50.00");
    }

    @Test
    void finalizeOrder_doesNothing_whenOrderNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        orderService.finalizeOrder(id); // must not throw
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_softDeletesOrder_whenCancelled() {
        var order = orderWithStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.delete(order.getId(), actorId);

        assertThat(order.isDeleted()).isTrue();
        assertThat(order.getDeletedBy()).isEqualTo(actorId);
        assertThat(order.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_softDeletesOrder_whenPending() {
        var order = orderWithStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        orderService.delete(order.getId(), actorId);

        assertThat(order.isDeleted()).isTrue();
    }

    @Test
    void delete_throwsConflict_whenOrderIsShipped() {
        var order = orderWithStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdAndDeletedFalse(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.delete(order.getId(), actorId))
                .isInstanceOf(OrderNotDeletableException.class);
        assertThat(order.isDeleted()).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order orderWithStatus(OrderStatus status) {
        var order = new Order();
        order.setCustomer(customerWithId(customerId));
        order.setOrderDate(Instant.now());
        order.setStatus(status);
        return order;
    }

    private Customer customerWithId(UUID id) {
        var c = new Customer();
        try {
            var f = Customer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        c.setName("Alice");
        c.setEmail("alice@example.com");
        return c;
    }

    private CreateOrderRequest createOrderRequest(UUID forCustomerId) {
        return new CreateOrderRequest(forCustomerId, Instant.now(),
                List.of(new OrderItemRequest("The Pragmatic Programmer", new BigDecimal("45.99"), 2)));
    }
}
