package com.spry.ticket.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class Event {
    private UUID id;
    private String name;
    private Instant eventDate;
    private String location;
    private int totalSeats;
    private Instant createdAt;
    private Instant updatedAt;
}
