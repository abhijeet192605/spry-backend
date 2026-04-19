package com.spry.ticket.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtService;
import com.spry.ticket.domain.exception.BookingNotFoundException;
import com.spry.ticket.domain.exception.BookingNotCancellableException;
import com.spry.ticket.domain.exception.HoldExpiredException;
import com.spry.ticket.domain.exception.HoldNotActiveException;
import com.spry.ticket.domain.exception.HoldNotFoundException;
import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.model.BookingStatus;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.port.in.BookingUseCase;
import com.spry.ticket.infrastructure.config.SecurityConfig;
import com.spry.ticket.infrastructure.web.BookingController;
import com.spry.ticket.infrastructure.web.dto.BookingResponse;
import com.spry.ticket.infrastructure.web.dto.ConfirmHoldRequest;
import com.spry.ticket.infrastructure.web.mapper.BookingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BookingUseCase bookingUseCase;
    @MockBean BookingMapper bookingMapper;
    @MockBean JwtService jwtService;

    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID HOLD_ID    = UUID.randomUUID();
    private static final UUID EVENT_ID   = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    // ── POST /api/v1/bookings/confirm ─────────────────────────────────────────

    @Test
    void confirmBooking_returns201_withBookingId() throws Exception {
        var req = new ConfirmHoldRequest(HOLD_ID, USER_ID);
        var booking = bookingModel();
        when(bookingUseCase.confirm(any())).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse(booking));

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.holdId").value(HOLD_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmBooking_returns410_whenHoldExpired() throws Exception {
        var req = new ConfirmHoldRequest(HOLD_ID, USER_ID);
        when(bookingUseCase.confirm(any()))
                .thenThrow(new HoldExpiredException(HOLD_ID, Instant.now().minusSeconds(60)));

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGone());
    }

    @Test
    void confirmBooking_returns409_whenHoldAlreadyConfirmed() throws Exception {
        var req = new ConfirmHoldRequest(HOLD_ID, USER_ID);
        when(bookingUseCase.confirm(any()))
                .thenThrow(new HoldNotActiveException(HOLD_ID, HoldStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmBooking_returns404_whenHoldNotFound() throws Exception {
        var req = new ConfirmHoldRequest(HOLD_ID, USER_ID);
        when(bookingUseCase.confirm(any()))
                .thenThrow(new HoldNotFoundException(HOLD_ID));

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmBooking_returns400_whenHoldIdMissing() throws Exception {
        String body = "{\"userId\":\"" + USER_ID + "\"}";

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/bookings/{id} ─────────────────────────────────────────────

    @Test
    void getBooking_returns200_whenFound() throws Exception {
        var booking = bookingModel();
        when(bookingUseCase.findById(eq(BOOKING_ID), isNull(), eq("ADMIN"))).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse(booking));

        mockMvc.perform(get("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seatCount").value(2));
    }

    @Test
    void getBooking_returns404_whenNotFound() throws Exception {
        when(bookingUseCase.findById(eq(BOOKING_ID), isNull(), eq("ADMIN")))
                .thenThrow(new BookingNotFoundException(BOOKING_ID));

        mockMvc.perform(get("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/bookings/{id} ──────────────────────────────────────────

    @Test
    void cancelBooking_returns204_onSuccess() throws Exception {
        doNothing().when(bookingUseCase).cancel(any());

        mockMvc.perform(delete("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelBooking_returns404_whenNotFound() throws Exception {
        doThrow(new BookingNotFoundException(BOOKING_ID))
                .when(bookingUseCase).cancel(any());

        mockMvc.perform(delete("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelBooking_returns409_whenEventAlreadyPassed() throws Exception {
        doThrow(new BookingNotCancellableException(BOOKING_ID, "event has already passed"))
                .when(bookingUseCase).cancel(any());

        mockMvc.perform(delete("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isConflict());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Booking bookingModel() {
        return Booking.builder()
                .id(BOOKING_ID)
                .holdId(HOLD_ID)
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .seatCount(2)
                .status(BookingStatus.CONFIRMED)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
    }

    private BookingResponse bookingResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getHoldId(), b.getEventId(),
                b.getUserId(), b.getSeatCount(), b.getStatus(), b.getCreatedAt());
    }
}
