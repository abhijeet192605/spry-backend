package com.spry.library.book.dto;

import com.spry.library.book.AvailabilityStatus;

public record BookFilterParams(String author, Integer year, AvailabilityStatus status) {

    public static BookFilterParams of(String author, Integer year, AvailabilityStatus status) {
        return new BookFilterParams(author, year, status);
    }
}
