package com.spry.ticket.infrastructure.web.mapper;

import com.spry.ticket.domain.model.Event;
import com.spry.ticket.infrastructure.web.dto.EventResponse;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {
    public EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getEventDate(),
                event.getLocation(),
                event.getTotalSeats(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
