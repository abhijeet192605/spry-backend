package com.spry.ticket.domain.port.in.command;

import java.util.UUID;

public record CreateHoldCommand(UUID eventId, UUID userId, int seatCount) {}
