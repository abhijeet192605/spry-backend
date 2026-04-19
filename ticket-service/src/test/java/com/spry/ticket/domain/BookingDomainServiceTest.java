package com.spry.ticket.domain;

import com.spry.ticket.domain.exception.BookingNotCancellableException;
import com.spry.ticket.domain.exception.BookingNotFoundException;
import com.spry.ticket.domain.exception.HoldExpiredException;
import com.spry.ticket.domain.exception.HoldNotActiveException;
import com.spry.ticket.domain.exception.HoldNotFoundException;
import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.model.BookingStatus;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.command.CancelBookingCommand;
import com.spry.ticket.domain.port.in.command.ConfirmHoldCommand;
import com.spry.ticket.domain.port.out.BookingRepository;
import com.spry.ticket.domain.port.out.DomainEventPublisher;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.domain.port.out.SeatHoldRepository;
import com.spry.ticket.domain.service.BookingDomainService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingDomainServiceTest {

    @Mock SeatHoldRepository seatHoldRepository;
    @Mock BookingRepository bookingRepository;
    @Mock EventRepository eventRepository;
    @Mock DomainEventPublisher eventPublisher;

    BookingDomainService bookingService;

    private final UUID holdId    = UUID.randomUUID();
    private final UUID eventId   = UUID.randomUUID();
    private final UUID userId    = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookingService = new BookingDomainService(seatHoldRepository, bookingRepository,
                eventRepository, eventPublisher, new SimpleMeterRegistry());
    }

    // ── confirm ───────────────────────────────────────────────────────────────

    @Test
    void confirm_savesBooking_andUpdatesHoldToConfirmed() {
        var hold = activeHold();
        var saved = bookingModel();

        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.of(hold));
        when(bookingRepository.existsByHoldId(holdId)).thenReturn(false);
        when(bookingRepository.save(any())).thenReturn(saved);

        var result = bookingService.confirm(new ConfirmHoldCommand(holdId, userId));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.CONFIRMED);
        verify(seatHoldRepository).save(hold);
        verify(eventPublisher).publishBookingConfirmed(saved);
    }

    @Test
    void confirm_throwsHoldExpired_whenHoldIsExpired() {
        var expiredHold = SeatHold.builder()
                .id(holdId).eventId(eventId).userId(userId).seatCount(2)
                .status(HoldStatus.ACTIVE)
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.of(expiredHold));

        assertThatThrownBy(() -> bookingService.confirm(new ConfirmHoldCommand(holdId, userId)))
                .isInstanceOf(HoldExpiredException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_throwsHoldNotActive_whenHoldAlreadyConfirmed() {
        var confirmedHold = SeatHold.builder()
                .id(holdId).eventId(eventId).userId(userId).seatCount(2)
                .status(HoldStatus.CONFIRMED)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.of(confirmedHold));

        assertThatThrownBy(() -> bookingService.confirm(new ConfirmHoldCommand(holdId, userId)))
                .isInstanceOf(HoldNotActiveException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_throwsHoldNotActive_whenHoldAlreadyExpiredStatus() {
        var expiredStatusHold = SeatHold.builder()
                .id(holdId).eventId(eventId).userId(userId).seatCount(2)
                .status(HoldStatus.EXPIRED)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.of(expiredStatusHold));

        assertThatThrownBy(() -> bookingService.confirm(new ConfirmHoldCommand(holdId, userId)))
                .isInstanceOf(HoldNotActiveException.class);
    }

    @Test
    void confirm_throwsHoldNotActive_whenBookingAlreadyExists_preventDoubleConfirm() {
        var hold = activeHold();

        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.of(hold));
        when(bookingRepository.existsByHoldId(holdId)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.confirm(new ConfirmHoldCommand(holdId, userId)))
                .isInstanceOf(HoldNotActiveException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_throwsHoldNotFound_whenHoldDoesNotExist() {
        when(seatHoldRepository.findByIdAndUserId(holdId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirm(new ConfirmHoldCommand(holdId, userId)))
                .isInstanceOf(HoldNotFoundException.class);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsBooking_forAdmin() {
        var booking = bookingModel();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        var result = bookingService.findById(bookingId, null, "ADMIN");

        assertThat(result.getId()).isEqualTo(bookingId);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void findById_throwsNotFound_whenBookingMissing() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findById(bookingId, null, "ADMIN"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_softCancelsBooking_whenEventIsInFuture() {
        var booking = bookingModel();
        var futureEvent = eventWith(Instant.now().plus(30, ChronoUnit.DAYS));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(futureEvent));

        bookingService.cancel(new CancelBookingCommand(bookingId, null, "ADMIN"));

        assertThat(booking.isDeleted()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getDeletedAt()).isNotNull();
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancel_throwsNotFound_whenBookingMissing() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancel(new CancelBookingCommand(bookingId, null, "ADMIN")))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancel_throwsNotCancellable_whenEventAlreadyPassed() {
        var booking = bookingModel();
        var pastEvent = eventWith(Instant.now().minusSeconds(3600));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(pastEvent));

        assertThatThrownBy(() -> bookingService.cancel(new CancelBookingCommand(bookingId, null, "ADMIN")))
                .isInstanceOf(BookingNotCancellableException.class);
        assertThat(booking.isDeleted()).isFalse();
    }

    @Test
    void cancel_throwsNotCancellable_whenAlreadyCancelled() {
        var booking = Booking.builder()
                .id(bookingId).holdId(holdId).eventId(eventId).userId(userId)
                .seatCount(2).status(BookingStatus.CANCELLED)
                .deleted(true).deletedAt(Instant.now())
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(new CancelBookingCommand(bookingId, null, "ADMIN")))
                .isInstanceOf(BookingNotCancellableException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SeatHold activeHold() {
        return SeatHold.builder()
                .id(holdId).eventId(eventId).userId(userId).seatCount(2)
                .status(HoldStatus.ACTIVE)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build();
    }

    private Booking bookingModel() {
        return Booking.builder()
                .id(bookingId).holdId(holdId).eventId(eventId).userId(userId)
                .seatCount(2).status(BookingStatus.CONFIRMED)
                .deleted(false).createdAt(Instant.now())
                .build();
    }

    private Event eventWith(Instant eventDate) {
        return Event.builder()
                .id(eventId).name("Conf").eventDate(eventDate)
                .location("SF").totalSeats(500)
                .build();
    }
}
