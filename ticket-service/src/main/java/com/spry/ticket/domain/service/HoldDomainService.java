package com.spry.ticket.domain.service;

import com.spry.ticket.domain.exception.EventAlreadyPassedException;
import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.InsufficientSeatsException;
import com.spry.ticket.domain.exception.InvalidSeatCountException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.HoldUseCase;
import com.spry.ticket.domain.port.in.command.CreateHoldCommand;
import com.spry.ticket.domain.port.out.DomainEventPublisher;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.domain.port.out.SeatHoldRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class HoldDomainService implements HoldUseCase {

    private final EventRepository eventRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final DomainEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Value("${hold.ttl-minutes:5}")
    private int holdTtlMinutes;

    @Value("${hold.max-seats:10}")
    private int maxSeatsPerHold;

    @Override
    @Transactional
    public SeatHold create(CreateHoldCommand cmd) {
        if (cmd.seatCount() < 1 || cmd.seatCount() > maxSeatsPerHold) {
            throw new InvalidSeatCountException(cmd.seatCount(), maxSeatsPerHold);
        }

        var event = eventRepository.findByIdWithLock(cmd.eventId())
                .orElseThrow(() -> new EventNotFoundException(cmd.eventId()));

        if (!event.getEventDate().isAfter(Instant.now())) {
            throw new EventAlreadyPassedException(cmd.eventId());
        }

        AvailabilityInfo availability = eventRepository.computeAvailability(cmd.eventId());
        if (availability.availableSeats() < cmd.seatCount()) {
            meterRegistry.counter("overbooking.rejected.total").increment();
            throw new InsufficientSeatsException(cmd.eventId(), cmd.seatCount(), availability.availableSeats());
        }

        SeatHold hold = SeatHold.builder()
                .eventId(cmd.eventId())
                .userId(cmd.userId())
                .seatCount(cmd.seatCount())
                .status(HoldStatus.ACTIVE)
                .expiresAt(Instant.now().plus(holdTtlMinutes, ChronoUnit.MINUTES))
                .build();

        SeatHold saved = seatHoldRepository.save(hold);
        meterRegistry.counter("holds.created.total").increment();
        eventPublisher.publishHoldCreated(saved);
        return saved;
    }
}
