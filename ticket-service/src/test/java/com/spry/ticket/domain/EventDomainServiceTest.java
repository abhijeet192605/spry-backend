package com.spry.ticket.domain;

import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.InvalidEventDateException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.in.command.CreateEventCommand;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.domain.service.EventDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDomainServiceTest {

    @Mock EventRepository eventRepository;

    EventDomainService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventDomainService(eventRepository);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesEvent_whenDateIsInFuture() {
        var cmd = new CreateEventCommand("Tech Conf", futureInstant(), "SF", 500, null);
        var saved = eventWith(UUID.randomUUID(), "Tech Conf", 500);
        when(eventRepository.save(any())).thenReturn(saved);

        var result = eventService.create(cmd);

        assertThat(result.getName()).isEqualTo("Tech Conf");
        assertThat(result.getTotalSeats()).isEqualTo(500);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void create_throwsInvalidEventDate_whenDateIsNotFuture() {
        var cmd = new CreateEventCommand("Past Event", Instant.now().minusSeconds(1), "NYC", 100, null);

        assertThatThrownBy(() -> eventService.create(cmd))
                .isInstanceOf(InvalidEventDateException.class);
    }

    @Test
    void create_throwsInvalidEventDate_whenDateIsExactlyNow() throws InterruptedException {
        var now = Instant.now();
        var cmd = new CreateEventCommand("Now Event", now, "NYC", 100, null);

        assertThatThrownBy(() -> eventService.create(cmd))
                .isInstanceOf(InvalidEventDateException.class);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEvent_whenFound() {
        var id = UUID.randomUUID();
        var event = eventWith(id, "Conf", 200);
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));

        var result = eventService.findById(id);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getName()).isEqualTo("Conf");
    }

    @Test
    void findById_throwsNotFound_whenEventMissing() {
        var id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findById(id))
                .isInstanceOf(EventNotFoundException.class);
    }

    // ── getAvailability ───────────────────────────────────────────────────────

    @Test
    void getAvailability_returnsComputedInfo_whenEventExists() {
        var id = UUID.randomUUID();
        var event = eventWith(id, "Conf", 500);
        var info = new AvailabilityInfo(500, 120, 30, 350, Instant.now());
        when(eventRepository.findById(id)).thenReturn(Optional.of(event));
        when(eventRepository.computeAvailability(id)).thenReturn(info);

        var result = eventService.getAvailability(id);

        assertThat(result.totalSeats()).isEqualTo(500);
        assertThat(result.confirmedSeats()).isEqualTo(120);
        assertThat(result.activeHoldSeats()).isEqualTo(30);
        assertThat(result.availableSeats()).isEqualTo(350);
    }

    @Test
    void getAvailability_throwsNotFound_whenEventMissing() {
        var id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getAvailability(id))
                .isInstanceOf(EventNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Instant futureInstant() {
        return Instant.now().plus(30, ChronoUnit.DAYS);
    }

    private Event eventWith(UUID id, String name, int totalSeats) {
        return Event.builder()
                .id(id)
                .name(name)
                .eventDate(futureInstant())
                .location("SF")
                .totalSeats(totalSeats)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
