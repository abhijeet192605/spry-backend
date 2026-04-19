package com.spry.ticket.infrastructure.persistence.adapter;

import com.spry.ticket.domain.model.HoldStatus;
import com.spry.ticket.domain.model.SeatHold;
import com.spry.ticket.domain.port.out.SeatHoldRepository;
import com.spry.ticket.infrastructure.persistence.entity.SeatHoldEntity;
import com.spry.ticket.infrastructure.persistence.jpa.SeatHoldJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HoldPersistenceAdapter implements SeatHoldRepository {

    private final SeatHoldJpaRepository jpaRepository;

    @Override
    public SeatHold save(SeatHold hold) {
        return toDomain(jpaRepository.save(toEntity(hold)));
    }

    @Override
    public Optional<SeatHold> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SeatHold> findByIdAndUserId(UUID holdId, UUID userId) {
        if (userId == null) {
            return jpaRepository.findById(holdId).map(this::toDomain);
        }
        return jpaRepository.findByIdAndUserId(holdId, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public int updateExpiredHolds() {
        return jpaRepository.updateExpiredHolds(HoldStatus.ACTIVE, HoldStatus.EXPIRED, Instant.now());
    }

    private SeatHoldEntity toEntity(SeatHold hold) {
        SeatHoldEntity e = new SeatHoldEntity();
        if (hold.getId() != null) e.setId(hold.getId());
        e.setEventId(hold.getEventId());
        e.setUserId(hold.getUserId());
        e.setSeatCount(hold.getSeatCount());
        e.setStatus(hold.getStatus());
        e.setExpiresAt(hold.getExpiresAt());
        return e;
    }

    private SeatHold toDomain(SeatHoldEntity e) {
        return SeatHold.builder()
                .id(e.getId())
                .eventId(e.getEventId())
                .userId(e.getUserId())
                .seatCount(e.getSeatCount())
                .status(e.getStatus())
                .expiresAt(e.getExpiresAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
