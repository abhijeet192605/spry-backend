package com.spry.ticket.domain.port.in.command;

import java.util.UUID;

public record CancelBookingCommand(UUID bookingId, UUID userId, String role) {}
