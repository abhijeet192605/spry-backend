package com.spry.library.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.library.book.AvailabilityStatus;
import com.spry.library.book.Book;
import com.spry.library.user.User;
import com.spry.library.user.UserRole;
import com.spry.library.wishlist.WishlistEntry;
import com.spry.library.wishlist.WishlistRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock WishlistRepository wishlistRepository;
    @Mock NotificationLogRepository notificationLogRepository;

    NotificationConsumer consumer;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(wishlistRepository, notificationLogRepository,
                objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void consume_insertsNotificationForEachWishlistedUser() throws Exception {
        UUID bookId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String payload = objectMapper.writeValueAsString(Map.of(
                "bookId", bookId.toString(),
                "bookTitle", "Clean Code",
                "newStatus", "AVAILABLE"
        ));

        var record = new ConsumerRecord<String, String>("book.status.changed", 0, 42L, bookId.toString(), payload);

        when(notificationLogRepository.existsByKafkaEventId("book.status.changed-0-42")).thenReturn(false);
        when(wishlistRepository.findAllByBookId(bookId)).thenReturn(List.of(wishlistEntry(userId, bookId)));
        when(notificationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(record);

        var captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());

        NotificationLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getBookId()).isEqualTo(bookId);
        assertThat(log.getMessage()).contains("Notification prepared for " + userId);
        assertThat(log.getMessage()).contains("Book [Clean Code] is now available.");
        assertThat(log.getKafkaEventId()).isEqualTo("book.status.changed-0-42");
    }

    @Test
    void consume_savesOneNotificationLogPerWishlistUser() throws Exception {
        UUID bookId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String payload = objectMapper.writeValueAsString(Map.of(
                "bookId", bookId.toString(),
                "bookTitle", "The Pragmatic Programmer",
                "newStatus", "AVAILABLE"
        ));

        var record = new ConsumerRecord<String, String>("book.status.changed", 1, 10L, bookId.toString(), payload);

        when(notificationLogRepository.existsByKafkaEventId("book.status.changed-1-10")).thenReturn(false);
        when(wishlistRepository.findAllByBookId(bookId))
                .thenReturn(List.of(wishlistEntry(userId1, bookId), wishlistEntry(userId2, bookId)));
        when(notificationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(record);

        verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    void consume_skipsAlreadyProcessedMessage() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "bookId", UUID.randomUUID().toString(),
                "bookTitle", "Some Book",
                "newStatus", "AVAILABLE"
        ));
        var record = new ConsumerRecord<String, String>("book.status.changed", 0, 99L, "key", payload);

        when(notificationLogRepository.existsByKafkaEventId("book.status.changed-0-99")).thenReturn(true);

        consumer.consume(record);

        verify(wishlistRepository, never()).findAllByBookId(any());
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void consume_doesNothingWhenNoWishlistEntries() throws Exception {
        UUID bookId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of(
                "bookId", bookId.toString(),
                "bookTitle", "Rare Book",
                "newStatus", "AVAILABLE"
        ));
        var record = new ConsumerRecord<String, String>("book.status.changed", 0, 7L, "key", payload);

        when(notificationLogRepository.existsByKafkaEventId(any())).thenReturn(false);
        when(wishlistRepository.findAllByBookId(bookId)).thenReturn(List.of());

        consumer.consume(record);

        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void consume_throwsRuntimeException_whenPayloadIsMalformed() {
        var record = new ConsumerRecord<String, String>(
                "book.status.changed", 0, 5L, "key", "not-valid-json{{{");

        when(notificationLogRepository.existsByKafkaEventId("book.status.changed-0-5")).thenReturn(false);

        // RuntimeException re-thrown so Kafka routes the message to the DLQ
        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Notification processing failed");
    }

    @Test
    void consume_throwsRuntimeException_whenBookIdIsMissing() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "bookTitle", "Missing BookId Book",
                "newStatus", "AVAILABLE"
        ));
        var record = new ConsumerRecord<String, String>("book.status.changed", 0, 20L, "key", payload);

        when(notificationLogRepository.existsByKafkaEventId("book.status.changed-0-20")).thenReturn(false);

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Notification processing failed");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WishlistEntry wishlistEntry(UUID userId, UUID bookId) {
        var user = new User();
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        user.setRole(UserRole.READER);

        var book = new Book();
        try {
            var idField = Book.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(book, bookId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        book.setTitle("Clean Code");
        book.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        var entry = new WishlistEntry();
        entry.setUser(user);
        entry.setBook(book);
        return entry;
    }
}
