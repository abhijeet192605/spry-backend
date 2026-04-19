package com.spry.ticket.infrastructure.persistence.entity;

import com.spry.shared.entity.AuditEntity;
import com.spry.ticket.domain.model.HoldStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
@Getter
@Setter
@NoArgsConstructor
public class SeatHoldEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status = HoldStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
