package com.spry.order.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull Instant orderDate,
        @NotNull @NotEmpty @Size(max = 500) @Valid List<OrderItemRequest> items
) {}
