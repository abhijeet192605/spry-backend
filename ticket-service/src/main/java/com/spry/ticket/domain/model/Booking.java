package com.spry.ticket.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class Booking {
    private UUID id;
    private UUID holdId;
    private UUID eventId;
    private UUID userId;
    private int seatCount;
    private BookingStatus status;
    private boolean deleted;
    private Instant deletedAt;
    private UUID deletedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
