package com.spry.order.order.dto;

import com.spry.order.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull OrderStatus status,
        @NotNull Long version
) {}
