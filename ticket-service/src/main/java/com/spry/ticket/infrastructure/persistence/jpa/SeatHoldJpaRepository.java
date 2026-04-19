package com.spry.ticket.infrastructure.persistence.jpa;

import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.infrastructure.persistence.entity.SeatHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldJpaRepository extends JpaRepository<SeatHoldEntity, UUID> {

    Optional<SeatHoldEntity> findByIdAndUserId(UUID id, UUID userId);

    int countByStatusAndExpiresAtAfter(HoldStatus status, Instant after);

    @Modifying
    @Query("UPDATE SeatHoldEntity h SET h.status = :expired WHERE h.status = :active AND h.expiresAt < :now")
    int updateExpiredHolds(@Param("active") HoldStatus active,
                           @Param("expired") HoldStatus expired,
                           @Param("now") Instant now);
}
