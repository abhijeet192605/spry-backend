package com.spry.ticket.domain.service;

import com.spry.ticket.domain.exception.BookingAccessDeniedException;
import com.spry.ticket.domain.exception.BookingNotCancellableException;
import com.spry.ticket.domain.exception.BookingNotFoundException;
import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.HoldExpiredException;
import com.spry.ticket.domain.exception.HoldNotActiveException;
import com.spry.ticket.domain.exception.HoldNotFoundException;
import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.model.BookingStatus;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.BookingUseCase;
import com.spry.ticket.domain.port.in.command.CancelBookingCommand;
import com.spry.ticket.domain.port.in.command.ConfirmHoldCommand;
import com.spry.ticket.domain.port.out.BookingRepository;
import com.spry.ticket.domain.port.out.DomainEventPublisher;
import com.spry.ticket.domain.port.out.EventRepository;
import com.spry.ticket.domain.port.out.SeatHoldRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingDomainService implements BookingUseCase {

    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final DomainEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public Booking confirm(ConfirmHoldCommand cmd) {
        SeatHold hold = seatHoldRepository.findByIdAndUserId(cmd.holdId(), cmd.userId())
                .orElseThrow(() -> new HoldNotFoundException(cmd.holdId()));

        if (!hold.isActive()) {
            throw new HoldNotActiveException(cmd.holdId(), hold.getStatus());
        }

        if (hold.isExpired()) {
            throw new HoldExpiredException(cmd.holdId(), hold.getExpiresAt());
        }

        if (bookingRepository.existsByHoldId(cmd.holdId())) {
            throw new HoldNotActiveException(cmd.holdId(), HoldStatus.CONFIRMED);
        }

        Booking booking = Booking.builder()
                .holdId(hold.getId())
                .eventId(hold.getEventId())
                .userId(hold.getUserId())
                .seatCount(hold.getSeatCount())
                .status(BookingStatus.CONFIRMED)
                .deleted(false)
                .build();

        Booking saved = bookingRepository.save(booking);

        hold.setStatus(HoldStatus.CONFIRMED);
        seatHoldRepository.save(hold);

        meterRegistry.counter("bookings.confirmed.total").increment();
        eventPublisher.publishBookingConfirmed(saved);
        return saved;
    }

    @Override
    public Booking findById(UUID id, UUID userId, String role) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (!"ADMIN".equalsIgnoreCase(role) && !booking.getUserId().equals(userId)) {
            throw new BookingAccessDeniedException(id);
        }

        return booking;
    }

    @Override
    @Transactional
    public void cancel(CancelBookingCommand cmd) {
        Booking booking = bookingRepository.findById(cmd.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(cmd.bookingId()));

        if (!"ADMIN".equalsIgnoreCase(cmd.role()) && !booking.getUserId().equals(cmd.userId())) {
            throw new BookingAccessDeniedException(cmd.bookingId());
        }

        if (booking.isDeleted()) {
            throw new BookingNotCancellableException(cmd.bookingId(), "already cancelled");
        }

        var event = eventRepository.findById(booking.getEventId())
                .orElseThrow(() -> new EventNotFoundException(booking.getEventId()));

        if (!event.getEventDate().isAfter(Instant.now())) {
            throw new BookingNotCancellableException(cmd.bookingId(), "event has already passed");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setDeleted(true);
        booking.setDeletedAt(Instant.now());
        booking.setDeletedBy(cmd.userId());
        bookingRepository.save(booking);
    }
}
