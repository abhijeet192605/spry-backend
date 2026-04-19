package com.spry.ticket.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(
        UUID eventId,
        int totalSeats,
        int confirmedSeats,
        int activeHoldSeats,
        int availableSeats,
        Instant computedAt
) {}
