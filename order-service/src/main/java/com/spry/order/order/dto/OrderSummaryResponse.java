package com.spry.order.order.dto;

import com.spry.order.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        OrderStatus status,
        BigDecimal totalValue,
        Instant orderDate,
        int itemCount
) {}
