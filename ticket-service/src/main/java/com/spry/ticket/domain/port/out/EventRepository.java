package com.spry.ticket.domain.port.out;

import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
    Event save(Event event);
    Optional<Event> findById(UUID id);
    Optional<Event> findByIdWithLock(UUID id);
    AvailabilityInfo computeAvailability(UUID eventId);
}
