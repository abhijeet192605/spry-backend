package com.spry.ticket.infrastructure.persistence.adapter;

import com.spry.ticket.domain.model.Booking;
import com.spry.ticket.domain.port.out.BookingRepository;
import com.spry.ticket.infrastructure.persistence.entity.BookingEntity;
import com.spry.ticket.infrastructure.persistence.jpa.BookingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BookingPersistenceAdapter implements BookingRepository {

    private final BookingJpaRepository jpaRepository;

    @Override
    public Booking save(Booking booking) {
        return toDomain(jpaRepository.save(toEntity(booking)));
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByHoldId(UUID holdId) {
        return jpaRepository.existsByHoldId(holdId);
    }

    private BookingEntity toEntity(Booking booking) {
        BookingEntity e = new BookingEntity();
        if (booking.getId() != null) e.setId(booking.getId());
        e.setHoldId(booking.getHoldId());
        e.setEventId(booking.getEventId());
        e.setUserId(booking.getUserId());
        e.setSeatCount(booking.getSeatCount());
        e.setStatus(booking.getStatus());
        e.setDeleted(booking.isDeleted());
        e.setDeletedAt(booking.getDeletedAt());
        e.setDeletedBy(booking.getDeletedBy());
        return e;
    }

    private Booking toDomain(BookingEntity e) {
        return Booking.builder()
                .id(e.getId())
                .holdId(e.getHoldId())
                .eventId(e.getEventId())
                .userId(e.getUserId())
                .seatCount(e.getSeatCount())
                .status(e.getStatus())
                .deleted(e.isDeleted())
                .deletedAt(e.getDeletedAt())
                .deletedBy(e.getDeletedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
