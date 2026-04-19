package com.spry.ticket.infrastructure.persistence.entity;

import com.spry.shared.entity.SoftDeletableEntity;
import com.spry.ticket.domain.model.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hold_id", nullable = false, unique = true)
    private UUID holdId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;
}
