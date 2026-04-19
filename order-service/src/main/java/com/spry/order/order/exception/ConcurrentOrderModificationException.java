package com.spry.order.order.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ConcurrentOrderModificationException extends ApiException {

    public ConcurrentOrderModificationException(UUID id) {
        super("Order " + id + " was modified concurrently — re-fetch and retry");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
