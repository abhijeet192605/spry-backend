package com.spry.order.order.dto;

import com.spry.order.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalValue,
        Instant orderDate,
        Long version,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}
