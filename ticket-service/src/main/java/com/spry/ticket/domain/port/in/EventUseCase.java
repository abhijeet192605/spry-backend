package com.spry.ticket.domain.port.in;

import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.in.command.CreateEventCommand;

import java.util.UUID;

public interface EventUseCase {
    Event create(CreateEventCommand cmd);
    Event findById(UUID id);
    AvailabilityInfo getAvailability(UUID eventId);
}
