package com.spry.ticket.infrastructure.web.mapper;

import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.infrastructure.web.dto.BookingResponse;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {
    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getHoldId(),
                booking.getEventId(),
                booking.getUserId(),
                booking.getSeatCount(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
