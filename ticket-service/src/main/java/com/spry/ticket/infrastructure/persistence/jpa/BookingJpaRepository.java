package com.spry.ticket.infrastructure.persistence.jpa;

import com.spry.ticket.infrastructure.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {
    boolean existsByHoldId(UUID holdId);
}
