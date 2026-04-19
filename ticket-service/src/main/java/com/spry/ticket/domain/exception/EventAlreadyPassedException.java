package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EventAlreadyPassedException extends ApiException {
    public EventAlreadyPassedException(UUID eventId) {
        super("Event has already passed: " + eventId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
