package com.spry.order.customer.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException(String email) {
        super("A customer with email '" + email + "' already exists");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
