package com.spry.library.book.dto;

import com.spry.library.book.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "status is required")
        AvailabilityStatus status
) {}
