package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookingNotCancellableException extends ApiException {
    public BookingNotCancellableException(UUID bookingId, String reason) {
        super(String.format("Booking %s cannot be cancelled: %s", bookingId, reason));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
