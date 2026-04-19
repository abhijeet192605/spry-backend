package com.spry.ticket.infrastructure.persistence.adapter;

import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.infrastructure.persistence.entity.EventEntity;
import com.spry.ticket.infrastructure.persistence.jpa.EventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EventPersistenceAdapter implements EventRepository {

    private final EventJpaRepository jpaRepository;

    @Override
    public Event save(Event event) {
        return toDomain(jpaRepository.save(toEntity(event)));
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Event> findByIdWithLock(UUID id) {
        return jpaRepository.findByIdWithLock(id).map(this::toDomain);
    }

    @Override
    public AvailabilityInfo computeAvailability(UUID eventId) {
        EventEntity entity = jpaRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        int confirmed = jpaRepository.sumConfirmedSeats(eventId);
        int activeHolds = jpaRepository.sumActiveHoldSeats(eventId);
        int available = entity.getTotalSeats() - confirmed - activeHolds;
        return new AvailabilityInfo(entity.getTotalSeats(), confirmed, activeHolds, available, Instant.now());
    }

    private EventEntity toEntity(Event event) {
        EventEntity e = new EventEntity();
        if (event.getId() != null) e.setId(event.getId());
        e.setName(event.getName());
        e.setEventDate(event.getEventDate());
        e.setLocation(event.getLocation());
        e.setTotalSeats(event.getTotalSeats());
        return e;
    }

    private Event toDomain(EventEntity e) {
        return Event.builder()
                .id(e.getId())
                .name(e.getName())
                .eventDate(e.getEventDate())
                .location(e.getLocation())
                .totalSeats(e.getTotalSeats())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
