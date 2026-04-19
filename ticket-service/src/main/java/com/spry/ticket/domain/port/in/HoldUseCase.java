package com.spry.ticket.domain.port.in;

import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.command.CreateHoldCommand;

public interface HoldUseCase {
    SeatHold create(CreateHoldCommand cmd);
}
