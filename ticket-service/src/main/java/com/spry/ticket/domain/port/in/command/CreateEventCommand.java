package com.spry.ticket.domain.port.in.command;

import java.time.Instant;
import java.util.UUID;

public record CreateEventCommand(String name, Instant eventDate, String location, int totalSeats, UUID actorId) {}
