package com.spry.ticket.domain.model;

import java.time.Instant;

public record AvailabilityInfo(
        int totalSeats,
        int confirmedSeats,
        int activeHoldSeats,
        int availableSeats,
        Instant computedAt
) {}
