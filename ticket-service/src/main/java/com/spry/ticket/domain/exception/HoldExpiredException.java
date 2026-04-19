package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

public class HoldExpiredException extends ApiException {
    public HoldExpiredException(UUID holdId, Instant expiredAt) {
        super(String.format("Hold %s expired at %s. Please create a new hold.", holdId, expiredAt));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.GONE;
    }
}
