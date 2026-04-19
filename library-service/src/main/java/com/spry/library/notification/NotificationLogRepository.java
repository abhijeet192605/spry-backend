package com.spry.library.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByKafkaEventId(String kafkaEventId);

    @Modifying
    @Query("DELETE FROM NotificationLog n WHERE n.notifiedAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
