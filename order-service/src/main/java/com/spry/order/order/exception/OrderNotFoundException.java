package com.spry.order.order.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(UUID id) {
        super("Order not found: " + id);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
