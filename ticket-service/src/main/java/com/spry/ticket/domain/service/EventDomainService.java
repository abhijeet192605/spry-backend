package com.spry.ticket.domain.service;

import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.InvalidEventDateException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.in.EventUseCase;
import com.spry.ticket.domain.port.in.command.CreateEventCommand;
import com.spry.ticket.domain.port.out.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventDomainService implements EventUseCase {

    private final EventRepository eventRepository;

    @Override
    @Transactional
    public Event create(CreateEventCommand cmd) {
        if (!cmd.eventDate().isAfter(Instant.now())) {
            throw new InvalidEventDateException("Event date must be in the future");
        }
        Event event = Event.builder()
                .name(cmd.name())
                .eventDate(cmd.eventDate())
                .location(cmd.location())
                .totalSeats(cmd.totalSeats())
                .build();
        return eventRepository.save(event);
    }

    @Override
    public Event findById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    @Override
    public AvailabilityInfo getAvailability(UUID eventId) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return eventRepository.computeAvailability(eventId);
    }
}
