package com.spry.ticket.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateHoldRequest(
        @NotNull UUID eventId,
        @NotNull @Min(1) Integer seatCount,
        UUID userId
) {}
