package com.spry.order.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {}
