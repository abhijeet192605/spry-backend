package com.spry.library.book.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookNotDeletableException extends ApiException {

    public BookNotDeletableException(UUID id) {
        super("Book " + id + " cannot be deleted while it is borrowed");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
