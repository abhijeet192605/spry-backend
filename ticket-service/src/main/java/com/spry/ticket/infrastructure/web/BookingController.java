package com.spry.ticket.infrastructure.web;

import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.port.in.BookingUseCase;
import com.spry.ticket.domain.port.in.command.CancelBookingCommand;
import com.spry.ticket.domain.port.in.command.ConfirmHoldCommand;
import com.spry.ticket.infrastructure.web.dto.BookingResponse;
import com.spry.ticket.infrastructure.web.dto.ConfirmHoldRequest;
import com.spry.ticket.infrastructure.web.mapper.BookingMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingUseCase bookingUseCase;
    private final BookingMapper bookingMapper;

    @PostMapping("/confirm")
    public ResponseEntity<BookingResponse> confirm(@Valid @RequestBody ConfirmHoldRequest req) {
        ConfirmHoldCommand cmd = new ConfirmHoldCommand(req.holdId(), req.userId());

        Booking booking = bookingUseCase.confirm(cmd);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/bookings/{id}").buildAndExpand(booking.getId()).toUri();
        return ResponseEntity.created(location).body(bookingMapper.toResponse(booking));
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable UUID id) {
        Booking booking = bookingUseCase.findById(id, null, "ADMIN");
        return bookingMapper.toResponse(booking);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        CancelBookingCommand cmd = new CancelBookingCommand(id, null, "ADMIN");
        bookingUseCase.cancel(cmd);
        return ResponseEntity.noContent().build();
    }
}
