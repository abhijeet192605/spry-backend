package com.spry.order.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.customer.Customer;
import com.spry.order.customer.CustomerService;
import com.spry.order.order.dto.CreateOrderRequest;
import com.spry.order.order.dto.OrderMapper;
import com.spry.order.order.dto.OrderResponse;
import com.spry.order.order.dto.OrderSummaryResponse;
import com.spry.order.order.dto.UpdateStatusRequest;
import com.spry.order.order.exception.ConcurrentOrderModificationException;
import com.spry.order.order.exception.InvalidStatusTransitionException;
import com.spry.order.order.exception.OrderNotDeletableException;
import com.spry.order.order.exception.OrderNotFoundException;
import com.spry.order.outbox.OutboxEvent;
import com.spry.order.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final CustomerService customerService;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public OrderResponse create(CreateOrderRequest req) {
        Customer customer = customerService.findCustomer(req.customerId());

        var order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(req.orderDate());

        req.items().forEach(itemReq -> {
            var item = new OrderItem();
            item.setProductName(itemReq.productName());
            item.setUnitPrice(itemReq.unitPrice());
            item.setQuantity(itemReq.quantity());
            order.addItem(item);
        });

        orderRepository.saveAndFlush(order);
        outboxRepository.save(buildCreatedEvent(order));

        meterRegistry.counter("orders.created.total").increment();
        log.info("Order created: id={}, customerId={}", order.getId(), customer.getId());

        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return orderMapper.toResponse(findActiveOrder(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listForCustomer(UUID customerId, String statusParam, Pageable pageable) {
        OrderStatus status = null;
        if (statusParam != null) {
            status = OrderStatus.valueOf(statusParam.toUpperCase());
        }

        return orderRepository.findByCustomerIdAndDeletedFalse(customerId, status, pageable)
                .map(orderMapper::toSummary);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, UpdateStatusRequest req, UUID actorId) {
        var order = findActiveOrder(id);

        if (!Objects.equals(order.getVersion(), req.version())) {
            throw new ConcurrentOrderModificationException(id);
        }

        if (!isValidManualTransition(order.getStatus(), req.status())) {
            throw new InvalidStatusTransitionException(order.getStatus(), req.status());
        }

        order.setStatus(req.status());

        try {
            orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentOrderModificationException(id);
        }

        log.info("Order status updated: id={}, status={}, actor={}", id, req.status(), actorId);
        meterRegistry.counter("orders.status.updated.total", "status", req.status().name()).increment();

        return orderMapper.toResponse(order);
    }

    @Transactional
    public void finalizeOrder(UUID orderId) {
        orderRepository.findByIdAndDeletedFalse(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PENDING) {
                log.debug("Order {} already finalized (status={}), skipping", orderId, order.getStatus());
                return;
            }

            BigDecimal total = order.getItems().stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            order.setStatus(OrderStatus.CONFIRMED);
            order.setTotalValue(total);

            log.info("Order finalized: id={}, total={}", orderId, total);
        });
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        var order = findActiveOrder(id);

        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new OrderNotDeletableException(id);
        }

        order.softDelete(actorId);
        log.info("Order soft-deleted: id={}, actor={}", id, actorId);
    }

    private Order findActiveOrder(UUID id) {
        return orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static boolean isValidManualTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.CANCELLED;
            case CONFIRMED -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            default -> false;
        };
    }

    private OutboxEvent buildCreatedEvent(Order order) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "orderId", order.getId().toString(),
                    "customerId", order.getCustomer().getId().toString()
            ));
            return OutboxEvent.of(order.getId(), "OrderCreated", payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for order " + order.getId(), e);
        }
    }
}
