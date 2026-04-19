package com.spry.ticket.domain.port.out;

import com.spry.ticket.domain.model.SeatHold;

import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository {
    SeatHold save(SeatHold hold);
    Optional<SeatHold> findById(UUID id);
    Optional<SeatHold> findByIdAndUserId(UUID holdId, UUID userId);
    int updateExpiredHolds();
}
