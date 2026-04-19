package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookingNotFoundException extends ApiException {
    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
