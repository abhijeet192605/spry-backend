package com.spry.ticket.domain.port.in;

import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.port.in.command.CancelBookingCommand;
import com.spry.ticket.domain.port.in.command.ConfirmHoldCommand;

import java.util.UUID;

public interface BookingUseCase {
    Booking confirm(ConfirmHoldCommand cmd);
    Booking findById(UUID id, UUID userId, String role);
    void cancel(CancelBookingCommand cmd);
}
