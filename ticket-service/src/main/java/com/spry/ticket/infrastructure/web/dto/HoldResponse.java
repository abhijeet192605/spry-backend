package com.spry.ticket.infrastructure.web.dto;

import com.spry.ticket.domain.model.HoldStatus;

import java.time.Instant;
import java.util.UUID;

public record HoldResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        int seatCount,
        HoldStatus status,
        Instant expiresAt,
        Instant createdAt
) {}
