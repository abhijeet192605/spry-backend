package com.spry.order.order.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotDeletableException extends ApiException {

    public OrderNotDeletableException(UUID id) {
        super("Order " + id + " cannot be deleted in its current state");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
