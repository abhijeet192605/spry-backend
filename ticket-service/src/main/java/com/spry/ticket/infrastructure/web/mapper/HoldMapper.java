package com.spry.ticket.infrastructure.web.mapper;

import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.infrastructure.web.dto.HoldResponse;
import org.springframework.stereotype.Component;

@Component
public class HoldMapper {
    public HoldResponse toResponse(SeatHold hold) {
        return new HoldResponse(
                hold.getId(),
                hold.getEventId(),
                hold.getUserId(),
                hold.getSeatCount(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getCreatedAt()
        );
    }
}
