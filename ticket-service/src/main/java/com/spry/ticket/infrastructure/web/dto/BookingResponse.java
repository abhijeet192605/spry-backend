package com.spry.ticket.infrastructure.web.dto;

import com.spry.ticket.domain.model.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID holdId,
        UUID eventId,
        UUID userId,
        int seatCount,
        BookingStatus status,
        Instant createdAt
) {}
