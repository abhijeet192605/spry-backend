package com.spry.ticket.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class SeatHold {
    private UUID id;
    private UUID eventId;
    private UUID userId;
    private int seatCount;
    private HoldStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return HoldStatus.ACTIVE == status;
    }
}
