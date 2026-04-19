package com.spry.library.book.dto;

import com.spry.library.book.AvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record BookResponse(
        UUID id,
        String title,
        String author,
        String isbn,
        int publishedYear,
        AvailabilityStatus availabilityStatus,
        Instant createdAt,
        Instant updatedAt
) {}
