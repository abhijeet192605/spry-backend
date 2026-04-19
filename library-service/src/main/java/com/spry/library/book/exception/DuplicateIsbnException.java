package com.spry.library.book.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateIsbnException extends ApiException {

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN " + isbn + " already exists");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
