package com.spry.ticket.domain.port.out;

import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.model.SeatHold;

public interface DomainEventPublisher {
    void publishHoldCreated(SeatHold hold);
    void publishBookingConfirmed(Booking booking);
}
