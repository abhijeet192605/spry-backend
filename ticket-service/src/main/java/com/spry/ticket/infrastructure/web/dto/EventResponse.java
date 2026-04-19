package com.spry.ticket.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        Instant eventDate,
        String location,
        int totalSeats,
        Instant createdAt,
        Instant updatedAt
) {}
