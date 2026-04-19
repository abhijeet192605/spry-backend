package com.spry.ticket.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtService;
import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.exception.InsufficientSeatsException;
import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.HoldUseCase;
import com.spry.ticket.infrastructure.config.SecurityConfig;
import com.spry.ticket.infrastructure.web.HoldController;
import com.spry.ticket.infrastructure.web.dto.CreateHoldRequest;
import com.spry.ticket.infrastructure.web.dto.HoldResponse;
import com.spry.ticket.infrastructure.web.mapper.HoldMapper;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HoldController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class HoldControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HoldUseCase holdUseCase;
    @MockBean HoldMapper holdMapper;
    @MockBean JwtService jwtService;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID HOLD_ID  = UUID.randomUUID();

    // ── POST /api/v1/holds ────────────────────────────────────────────────────

    @Test
    void createHold_returns201_withHoldId_andExpiresAt() throws Exception {
        var req = new CreateHoldRequest(EVENT_ID, 2, USER_ID);
        var hold = holdModel();
        when(holdUseCase.create(any())).thenReturn(hold);
        when(holdMapper.toResponse(hold)).thenReturn(holdResponse(hold));

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(HOLD_ID.toString()))
                .andExpect(jsonPath("$.seatCount").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void createHold_returns409_whenInsufficientSeats() throws Exception {
        var req = new CreateHoldRequest(EVENT_ID, 10, USER_ID);
        when(holdUseCase.create(any()))
                .thenThrow(new InsufficientSeatsException(EVENT_ID, 10, 3));

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void createHold_returns404_whenEventNotFound() throws Exception {
        var req = new CreateHoldRequest(EVENT_ID, 2, USER_ID);
        when(holdUseCase.create(any()))
                .thenThrow(new EventNotFoundException(EVENT_ID));

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createHold_returns400_whenSeatCountIsZero() throws Exception {
        var req = new CreateHoldRequest(EVENT_ID, 0, USER_ID);

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createHold_returns400_whenEventIdMissing() throws Exception {
        String body = "{\"seatCount\":2,\"userId\":\"" + USER_ID + "\"}";

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SeatHold holdModel() {
        return SeatHold.builder()
                .id(HOLD_ID)
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .seatCount(2)
                .status(HoldStatus.ACTIVE)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build();
    }

    private HoldResponse holdResponse(SeatHold h) {
        return new HoldResponse(h.getId(), h.getEventId(), h.getUserId(),
                h.getSeatCount(), h.getStatus(), h.getExpiresAt(), h.getCreatedAt());
    }
}
