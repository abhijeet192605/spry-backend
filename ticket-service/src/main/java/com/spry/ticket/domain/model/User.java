package com.spry.ticket.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class User {
    private UUID id;
    private String name;
    private String email;
    private Instant createdAt;
}
