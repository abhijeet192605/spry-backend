package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EventNotFoundException extends ApiException {
    public EventNotFoundException(UUID id) {
        super("Event not found: " + id);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
