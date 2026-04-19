package com.spry.order.customer.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CustomerNotFoundException extends ApiException {

    public CustomerNotFoundException(UUID id) {
        super("Customer not found: " + id);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
