package com.spry.ticket.domain.port.in.command;

import java.util.UUID;

public record ConfirmHoldCommand(UUID holdId, UUID userId) {}
