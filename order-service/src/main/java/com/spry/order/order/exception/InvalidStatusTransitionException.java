package com.spry.order.order.exception;

import com.spry.order.order.OrderStatus;
import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
