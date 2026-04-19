package com.spry.ticket.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.out.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishHoldCreated(SeatHold hold) {
        try {
            Map<String, Object> payload = Map.of(
                    "holdId", hold.getId(),
                    "eventId", hold.getEventId(),
                    "userId", String.valueOf(hold.getUserId()),
                    "seatCount", hold.getSeatCount(),
                    "expiresAt", hold.getExpiresAt().toString()
            );
            kafkaTemplate.send("seat.hold.created", hold.getId().toString(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Failed to publish seat.hold.created for hold {}: {}", hold.getId(), e.getMessage());
        }
    }

    @Override
    public void publishBookingConfirmed(Booking booking) {
        try {
            Map<String, Object> payload = Map.of(
                    "bookingId", booking.getId(),
                    "holdId", booking.getHoldId(),
                    "eventId", booking.getEventId(),
                    "userId", String.valueOf(booking.getUserId()),
                    "seatCount", booking.getSeatCount(),
                    "confirmedAt", Instant.now().toString()
            );
            kafkaTemplate.send("booking.confirmed", booking.getId().toString(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Failed to publish booking.confirmed for booking {}: {}", booking.getId(), e.getMessage());
        }
    }
}
