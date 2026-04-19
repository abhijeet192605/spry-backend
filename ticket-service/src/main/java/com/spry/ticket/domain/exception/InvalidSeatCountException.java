package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidSeatCountException extends ApiException {
    public InvalidSeatCountException(int requested, int max) {
        super(String.format("Seat count %d is invalid. Must be between 1 and %d", requested, max));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
