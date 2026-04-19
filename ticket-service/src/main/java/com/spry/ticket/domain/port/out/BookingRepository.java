package com.spry.ticket.domain.port.out;

import com.spry.ticket.domain.model.Booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(UUID id);
    boolean existsByHoldId(UUID holdId);
}
