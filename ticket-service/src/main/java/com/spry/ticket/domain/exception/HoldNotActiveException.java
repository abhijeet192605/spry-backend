package com.spry.ticket.domain.exception;

import com.spry.shared.exception.ApiException;
import com.spry.ticket.domain.model.HoldStatus;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class HoldNotActiveException extends ApiException {
    public HoldNotActiveException(UUID holdId, HoldStatus currentStatus) {
        super(String.format("Hold %s cannot be confirmed: current status is %s", holdId, currentStatus));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
