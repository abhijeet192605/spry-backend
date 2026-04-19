package com.spry.order.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndDeletedFalse(UUID id);

    @Query("""
            SELECT o FROM Order o WHERE o.customer.id = :customerId AND o.deleted = false
            AND (:status IS NULL OR o.status = :status)
            """)
    Page<Order> findByCustomerIdAndDeletedFalse(
            @Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            Pageable pageable);

    List<Order> findByStatusAndDeletedFalseAndCreatedAtBefore(OrderStatus status, Instant threshold);

    default List<Order> findStuckPendingOrders(Instant threshold) {
        return findByStatusAndDeletedFalseAndCreatedAtBefore(OrderStatus.PENDING, threshold);
    }

    long countByStatus(OrderStatus status);
}
