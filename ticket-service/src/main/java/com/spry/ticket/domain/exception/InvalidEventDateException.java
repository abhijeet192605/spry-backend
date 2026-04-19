package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidEventDateException extends ApiException {
    public InvalidEventDateException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
