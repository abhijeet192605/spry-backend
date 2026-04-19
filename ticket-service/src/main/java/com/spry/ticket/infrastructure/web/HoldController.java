package com.spry.ticket.infrastructure.web;

import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.in.HoldUseCase;
import com.spry.ticket.domain.port.in.command.CreateHoldCommand;
import com.spry.ticket.infrastructure.web.dto.CreateHoldRequest;
import com.spry.ticket.infrastructure.web.dto.HoldResponse;
import com.spry.ticket.infrastructure.web.mapper.HoldMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldUseCase holdUseCase;
    private final HoldMapper holdMapper;

    @PostMapping
    public ResponseEntity<HoldResponse> create(@Valid @RequestBody CreateHoldRequest req) {
        CreateHoldCommand cmd = new CreateHoldCommand(req.eventId(), req.userId(), req.seatCount());

        SeatHold hold = holdUseCase.create(cmd);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(hold.getId()).toUri();
        return ResponseEntity.created(location).body(holdMapper.toResponse(hold));
    }
}
