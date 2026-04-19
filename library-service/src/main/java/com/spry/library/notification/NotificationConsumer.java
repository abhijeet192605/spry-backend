package com.spry.library.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.spry.library.wishlist.WishlistRepository;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final WishlistRepository wishlistRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "book.status.changed", groupId = "library-notifications")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        String kafkaEventId = record.topic() + "-" + record.partition() + "-" + record.offset();

        // Skip already-processed messages (at-least-once delivery safety net)
        if (notificationLogRepository.existsByKafkaEventId(kafkaEventId)) {
            log.debug("Skipping duplicate message: kafkaEventId={}", kafkaEventId);
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID bookId = UUID.fromString(payload.get("bookId").asText());
            String bookTitle = payload.get("bookTitle").asText();

            var wishlistEntries = wishlistRepository.findAllByBookId(bookId);
            if (wishlistEntries.isEmpty()) {
                return;
            }

            for (var entry : wishlistEntries) {
                UUID userId = entry.getUser().getId();
                String message = "Notification prepared for " + userId
                        + ": Book [" + bookTitle + "] is now available.";

                notificationLogRepository.save(
                        NotificationLog.of(userId, bookId, kafkaEventId, message));

                log.info(message);
            }

            meterRegistry.counter("wishlist.notifications.sent.total")
                    .increment(wishlistEntries.size());

        } catch (Exception e) {
            meterRegistry.counter("notification.failures.total").increment();
            log.error("Failed to process book.status.changed event at offset={}: {}",
                    record.offset(), e.getMessage(), e);
            throw new RuntimeException("Notification processing failed", e);
        }
    }
}
