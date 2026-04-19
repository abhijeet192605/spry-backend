package com.spry.ticket.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmHoldRequest(
        @NotNull UUID holdId,
        UUID userId
) {}
