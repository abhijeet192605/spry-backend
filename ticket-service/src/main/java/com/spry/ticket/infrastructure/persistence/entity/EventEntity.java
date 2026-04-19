package com.spry.ticket.infrastructure.persistence.entity;

import com.spry.shared.entity.AuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class EventEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    @Column(nullable = false, length = 400)
    private String location;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;
}
