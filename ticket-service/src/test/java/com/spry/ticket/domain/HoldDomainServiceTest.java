package com.spry.ticket.domain;

import com.spry.ticket.domain.exception.EventAlreadyPassedException;
import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.InsufficientSeatsException;
import com.spry.ticket.domain.exception.InvalidSeatCountException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.command.CreateHoldCommand;
import com.spry.ticket.domain.port.out.DomainEventPublisher;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.domain.port.out.SeatHoldRepository;
import com.spry.ticket.domain.service.HoldDomainService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldDomainServiceTest {

    @Mock EventRepository eventRepository;
    @Mock SeatHoldRepository seatHoldRepository;
    @Mock DomainEventPublisher eventPublisher;

    HoldDomainService holdService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        holdService = new HoldDomainService(eventRepository, seatHoldRepository,
                eventPublisher, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(holdService, "holdTtlMinutes", 5);
        ReflectionTestUtils.setField(holdService, "maxSeatsPerHold", 10);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesHold_whenSeatsAvailable() {
        var event = futureEvent(500);
        var availability = new AvailabilityInfo(500, 0, 0, 500, Instant.now());
        var saved = holdModel(2);

        when(eventRepository.findByIdWithLock(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.computeAvailability(eventId)).thenReturn(availability);
        when(seatHoldRepository.save(any())).thenReturn(saved);

        var result = holdService.create(new CreateHoldCommand(eventId, userId, 2));

        assertThat(result.getSeatCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        verify(seatHoldRepository).save(any(SeatHold.class));
        verify(eventPublisher).publishHoldCreated(saved);
    }

    @Test
    void create_throwsInsufficientSeats_whenNotEnoughSeatsAvailable() {
        var event = futureEvent(5);
        var availability = new AvailabilityInfo(5, 3, 2, 0, Instant.now());

        when(eventRepository.findByIdWithLock(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.computeAvailability(eventId)).thenReturn(availability);

        assertThatThrownBy(() -> holdService.create(new CreateHoldCommand(eventId, userId, 2)))
                .isInstanceOf(InsufficientSeatsException.class);
        verify(seatHoldRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenEventDoesNotExist() {
        when(eventRepository.findByIdWithLock(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdService.create(new CreateHoldCommand(eventId, userId, 2)))
                .isInstanceOf(EventNotFoundException.class);
        verify(seatHoldRepository, never()).save(any());
    }

    @Test
    void create_throwsEventAlreadyPassed_whenEventDateInPast() {
        var pastEvent = Event.builder()
                .id(eventId)
                .name("Old Conf")
                .eventDate(Instant.now().minusSeconds(3600))
                .location("NYC")
                .totalSeats(100)
                .build();
        when(eventRepository.findByIdWithLock(eventId)).thenReturn(Optional.of(pastEvent));

        assertThatThrownBy(() -> holdService.create(new CreateHoldCommand(eventId, userId, 2)))
                .isInstanceOf(EventAlreadyPassedException.class);
        verify(seatHoldRepository, never()).save(any());
    }

    @Test
    void create_throwsInvalidSeatCount_whenSeatCountExceedsMax() {
        assertThatThrownBy(() -> holdService.create(new CreateHoldCommand(eventId, userId, 11)))
                .isInstanceOf(InvalidSeatCountException.class);
        verify(eventRepository, never()).findByIdWithLock(any());
    }

    @Test
    void create_throwsInvalidSeatCount_whenSeatCountIsZero() {
        assertThatThrownBy(() -> holdService.create(new CreateHoldCommand(eventId, userId, 0)))
                .isInstanceOf(InvalidSeatCountException.class);
    }

    @Test
    void create_holdExpiresAtNowPlusTtl() {
        var event = futureEvent(500);
        var availability = new AvailabilityInfo(500, 0, 0, 500, Instant.now());
        var saved = holdModel(2);

        when(eventRepository.findByIdWithLock(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.computeAvailability(eventId)).thenReturn(availability);
        when(seatHoldRepository.save(any())).thenReturn(saved);

        var before = Instant.now().plus(4, ChronoUnit.MINUTES);
        holdService.create(new CreateHoldCommand(eventId, userId, 2));
        var after = Instant.now().plus(6, ChronoUnit.MINUTES);

        var captor = org.mockito.ArgumentCaptor.forClass(SeatHold.class);
        verify(seatHoldRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isAfter(before).isBefore(after);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Event futureEvent(int totalSeats) {
        return Event.builder()
                .id(eventId)
                .name("Tech Conf")
                .eventDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .location("SF")
                .totalSeats(totalSeats)
                .build();
    }

    private SeatHold holdModel(int seatCount) {
        return SeatHold.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .userId(userId)
                .seatCount(seatCount)
                .status(HoldStatus.ACTIVE)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build();
    }
}
