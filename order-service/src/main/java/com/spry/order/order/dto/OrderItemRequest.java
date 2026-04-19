package com.spry.order.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank @Size(max = 300) String productName,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @NotNull @Min(1) Integer quantity
) {}
