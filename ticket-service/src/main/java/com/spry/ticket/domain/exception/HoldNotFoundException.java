package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class HoldNotFoundException extends ApiException {
    public HoldNotFoundException(UUID holdId) {
        super("Hold not found: " + holdId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
