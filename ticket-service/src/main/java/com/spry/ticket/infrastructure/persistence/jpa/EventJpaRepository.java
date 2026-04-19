package com.spry.ticket.infrastructure.persistence.jpa;

import com.spry.ticket.infrastructure.persistence.entity.EventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventEntity e WHERE e.id = :id")
    Optional<EventEntity> findByIdWithLock(@Param("id") UUID id);

    @Query(value = "SELECT COALESCE(SUM(b.seat_count), 0) FROM bookings b " +
            "WHERE b.event_id = :eventId AND b.status = 'CONFIRMED' AND b.deleted = false",
            nativeQuery = true)
    int sumConfirmedSeats(@Param("eventId") UUID eventId);

    @Query(value = "SELECT COALESCE(SUM(h.seat_count), 0) FROM seat_holds h " +
            "WHERE h.event_id = :eventId AND h.status = 'ACTIVE' AND h.expires_at > now()",
            nativeQuery = true)
    int sumActiveHoldSeats(@Param("eventId") UUID eventId);
}
