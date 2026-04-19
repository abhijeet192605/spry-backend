package com.spry.library.book.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookNotFoundException extends ApiException {

    public BookNotFoundException(UUID id) {
        super("Book not found: " + id);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
