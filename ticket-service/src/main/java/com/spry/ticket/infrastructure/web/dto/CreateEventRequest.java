package com.spry.ticket.infrastructure.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank @Size(max = 300) String name,
        @NotNull @Future Instant eventDate,
        @NotBlank @Size(max = 400) String location,
        @NotNull @Min(1) Integer totalSeats
) {}
