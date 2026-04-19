package com.spry.library.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    // topic-partition-offset — idempotency key scoped to (kafka_event_id, user_id)
    @Column(name = "kafka_event_id", nullable = false, length = 100)
    private String kafkaEventId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();

    public static NotificationLog of(UUID userId, UUID bookId, String kafkaEventId, String message) {
        var log = new NotificationLog();
        log.userId = userId;
        log.bookId = bookId;
        log.kafkaEventId = kafkaEventId;
        log.message = message;
        return log;
    }
}
