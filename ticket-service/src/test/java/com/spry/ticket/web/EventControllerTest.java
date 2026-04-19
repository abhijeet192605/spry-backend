package com.spry.ticket.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtService;
import com.spry.ticket.domain.exception.EventNotFoundException;
import com.spry.ticket.domain.model.AvailabilityInfo;
import com.spry.ticket.domain.model.Event;
import com.spry.ticket.domain.port.in.EventUseCase;
import com.spry.ticket.infrastructure.config.SecurityConfig;
import com.spry.ticket.infrastructure.web.EventController;
import com.spry.ticket.infrastructure.web.dto.CreateEventRequest;
import com.spry.ticket.infrastructure.web.dto.EventResponse;
import com.spry.ticket.infrastructure.web.mapper.EventMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EventControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean EventUseCase eventUseCase;
    @MockBean EventMapper eventMapper;
    @MockBean JwtService jwtService;

    private static final UUID EVENT_ID = UUID.randomUUID();

    // ── POST /api/v1/events ───────────────────────────────────────────────────

    @Test
    void createEvent_returns201_withLocationHeader() throws Exception {
        var req = new CreateEventRequest("Tech Conf 2027", futureInstant(), "San Francisco", 500);
        var event = eventModel();
        when(eventUseCase.create(any())).thenReturn(event);
        when(eventMapper.toResponse(event)).thenReturn(eventResponse(event));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Tech Conf 2027"))
                .andExpect(jsonPath("$.totalSeats").value(500));
    }

    @Test
    void createEvent_returns400_whenEventDateInPast() throws Exception {
        var req = new CreateEventRequest("Old Event", Instant.now().minusSeconds(3600), "NYC", 100);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createEvent_returns400_whenTotalSeatsIsZero() throws Exception {
        var req = new CreateEventRequest("Zero Seats Event", futureInstant(), "NYC", 0);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createEvent_returns400_whenNameIsBlank() throws Exception {
        var req = new CreateEventRequest("", futureInstant(), "NYC", 100);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ── GET /api/v1/events/{id} ───────────────────────────────────────────────

    @Test
    void getEvent_returns200_whenFound() throws Exception {
        var event = eventModel();
        when(eventUseCase.findById(EVENT_ID)).thenReturn(event);
        when(eventMapper.toResponse(event)).thenReturn(eventResponse(event));

        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.totalSeats").value(500));
    }

    @Test
    void getEvent_returns404_whenNotFound() throws Exception {
        when(eventUseCase.findById(EVENT_ID)).thenThrow(new EventNotFoundException(EVENT_ID));

        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/events/{id}/availability ─────────────────────────────────

    @Test
    void getAvailability_returns200_withComputedSeats() throws Exception {
        var info = new AvailabilityInfo(500, 120, 30, 350, Instant.now());
        when(eventUseCase.getAvailability(EVENT_ID)).thenReturn(info);

        mockMvc.perform(get("/api/v1/events/{id}/availability", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.totalSeats").value(500))
                .andExpect(jsonPath("$.confirmedSeats").value(120))
                .andExpect(jsonPath("$.activeHoldSeats").value(30))
                .andExpect(jsonPath("$.availableSeats").value(350));
    }

    @Test
    void getAvailability_returns404_whenEventNotFound() throws Exception {
        when(eventUseCase.getAvailability(EVENT_ID)).thenThrow(new EventNotFoundException(EVENT_ID));

        mockMvc.perform(get("/api/v1/events/{id}/availability", EVENT_ID))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Instant futureInstant() {
        return Instant.now().plus(30, ChronoUnit.DAYS);
    }

    private Event eventModel() {
        return Event.builder()
                .id(EVENT_ID)
                .name("Tech Conf 2027")
                .eventDate(futureInstant())
                .location("San Francisco")
                .totalSeats(500)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private EventResponse eventResponse(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getEventDate(),
                e.getLocation(), e.getTotalSeats(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
