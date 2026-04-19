package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookingAccessDeniedException extends ApiException {
    public BookingAccessDeniedException(UUID bookingId) {
        super("Access denied to booking: " + bookingId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
