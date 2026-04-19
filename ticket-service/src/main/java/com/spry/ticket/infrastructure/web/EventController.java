package com.spry.ticket.infrastructure.web;

import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.in.EventUseCase;
import com.spry.ticket.domain.port.in.command.CreateEventCommand;
import com.spry.ticket.infrastructure.web.dto.AvailabilityResponse;
import com.spry.ticket.infrastructure.web.dto.CreateEventRequest;
import com.spry.ticket.infrastructure.web.dto.EventResponse;
import com.spry.ticket.infrastructure.web.mapper.EventMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventUseCase eventUseCase;
    private final EventMapper eventMapper;

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest req) {
        CreateEventCommand cmd = new CreateEventCommand(
                req.name(), req.eventDate(), req.location(), req.totalSeats(), null);

        Event event = eventUseCase.create(cmd);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(event.getId()).toUri();
        return ResponseEntity.created(location).body(eventMapper.toResponse(event));
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable UUID id) {
        return eventMapper.toResponse(eventUseCase.findById(id));
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse getAvailability(@PathVariable UUID id) {
        AvailabilityInfo info = eventUseCase.getAvailability(id);
        return new AvailabilityResponse(id, info.totalSeats(), info.confirmedSeats(),
                info.activeHoldSeats(), info.availableSeats(), info.computedAt());
    }
}
