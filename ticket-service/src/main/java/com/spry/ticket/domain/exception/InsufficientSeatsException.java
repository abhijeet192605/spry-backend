package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InsufficientSeatsException extends ApiException {
    public InsufficientSeatsException(UUID eventId, int requested, int available) {
        super(String.format("Insufficient seats for event %s: requested %d, available %d", eventId, requested, available));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
