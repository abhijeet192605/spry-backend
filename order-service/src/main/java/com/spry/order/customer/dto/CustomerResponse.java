package com.spry.order.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String address,
        Instant createdAt
) {}
