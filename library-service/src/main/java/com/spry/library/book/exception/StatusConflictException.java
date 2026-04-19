package com.spry.library.book.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StatusConflictException extends ApiException {

    public StatusConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
