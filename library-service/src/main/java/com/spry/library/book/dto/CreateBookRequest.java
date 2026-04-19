package com.spry.library.book.dto;

import com.spry.library.book.AvailabilityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Year;

public record CreateBookRequest(
        @NotBlank @Size(max = 300)
        String title,

        @NotBlank @Size(max = 200)
        String author,

        @NotBlank
        @Pattern(regexp = "^97[89]\\d{10}$", message = "must be a valid ISBN-13")
        String isbn,

        @NotNull
        @Min(1000) @Max(9999)
        Integer publishedYear,

        // Optional — defaults to AVAILABLE when omitted
        AvailabilityStatus availabilityStatus
) {
    public CreateBookRequest {
        if (publishedYear != null && publishedYear > Year.now().getValue()) {
            throw new IllegalArgumentException("publishedYear cannot be in the future");
        }
    }
}
