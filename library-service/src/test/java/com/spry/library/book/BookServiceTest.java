package com.spry.library.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.library.book.dto.BookFilterParams;
import com.spry.library.book.dto.BookMapper;
import com.spry.library.book.dto.BookMapperImpl;
import com.spry.library.book.dto.BookResponse;
import com.spry.library.book.dto.CreateBookRequest;
import com.spry.library.book.dto.UpdateBookRequest;
import com.spry.library.book.exception.BookNotDeletableException;
import com.spry.library.book.exception.BookNotFoundException;
import com.spry.library.book.exception.DuplicateIsbnException;
import com.spry.library.book.exception.StatusConflictException;
import com.spry.library.outbox.OutboxEvent;
import com.spry.library.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock BookRepository bookRepository;
    @Mock OutboxEventRepository outboxRepository;

    BookService bookService;

    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BookMapper mapper = new BookMapperImpl();
        MeterRegistry registry = new SimpleMeterRegistry();
        bookService = new BookService(bookRepository, outboxRepository, mapper, new ObjectMapper(), registry);
    }

    @Test
    void create_savesBookAndReturnsResponse() {
        var req = new CreateBookRequest("Clean Code", "Robert Martin", "9780132350884", 2008, null);
        when(bookRepository.existsByIsbn(req.isbn())).thenReturn(false);
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookResponse response = bookService.create(req, actorId);

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void create_throwsDuplicateIsbn_whenIsbnAlreadyExists() {
        var req = new CreateBookRequest("Book", "Author", "9780132350884", 2008, null);
        when(bookRepository.existsByIsbn(req.isbn())).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(req, actorId))
                .isInstanceOf(DuplicateIsbnException.class);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void create_respectsExplicitAvailabilityStatus() {
        var req = new CreateBookRequest("Book", "Author", "9780132350884", 2008, AvailabilityStatus.BORROWED);
        when(bookRepository.existsByIsbn(any())).thenReturn(false);
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookResponse response = bookService.create(req, actorId);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.BORROWED);
    }

    @Test
    void getById_throwsNotFound_whenBookDeleted() {
        UUID id = UUID.randomUUID();
        when(bookRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getById(id))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void updateStatus_toAvailable_insertsOutboxEvent() {
        var book = bookWithStatus(AvailabilityStatus.BORROWED);
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        bookService.updateStatus(book.getId(), AvailabilityStatus.AVAILABLE, actorId);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("BookStatusChanged");
        assertThat(captor.getValue().getPayload()).contains("AVAILABLE");
    }

    @Test
    void updateStatus_toBorrowed_doesNotInsertOutboxEvent() {
        var book = bookWithStatus(AvailabilityStatus.AVAILABLE);
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        bookService.updateStatus(book.getId(), AvailabilityStatus.BORROWED, actorId);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void updateStatus_throwsConflict_whenAlreadyInRequestedStatus() {
        var book = bookWithStatus(AvailabilityStatus.AVAILABLE);
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> bookService.updateStatus(book.getId(), AvailabilityStatus.AVAILABLE, actorId))
                .isInstanceOf(StatusConflictException.class);
    }

    @Test
    void delete_throwsConflict_whenBookIsBorrowed() {
        var book = bookWithStatus(AvailabilityStatus.BORROWED);
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> bookService.delete(book.getId(), actorId))
                .isInstanceOf(BookNotDeletableException.class);
    }

    @Test
    void delete_softDeletesBook_whenAvailable() {
        var book = bookWithStatus(AvailabilityStatus.AVAILABLE);
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        bookService.delete(book.getId(), actorId);

        assertThat(book.isDeleted()).isTrue();
        assertThat(book.getDeletedBy()).isEqualTo(actorId);
        assertThat(book.getDeletedAt()).isNotNull();
    }

    @Test
    void list_delegatesFiltersToRepository() {
        var pageable = PageRequest.of(0, 10);
        var filters = BookFilterParams.of("Martin", 2008, AvailabilityStatus.AVAILABLE);
        when(bookRepository.findWithFilters("Martin", 2008, AvailabilityStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        bookService.list(filters, pageable);

        verify(bookRepository).findWithFilters("Martin", 2008, AvailabilityStatus.AVAILABLE, pageable);
    }

    @Test
    void update_throwsDuplicateIsbn_whenIsbnConflictsWithAnotherBook() {
        var book = bookWithStatus(AvailabilityStatus.AVAILABLE);
        book.setIsbn("9780132350884");
        when(bookRepository.findByIdAndDeletedFalse(book.getId())).thenReturn(Optional.of(book));

        var req = new UpdateBookRequest("Title", "Author", "9780596007126", 2005);
        when(bookRepository.existsByIsbnAndIdNot(req.isbn(), book.getId())).thenReturn(true);

        assertThatThrownBy(() -> bookService.update(book.getId(), req, actorId))
                .isInstanceOf(DuplicateIsbnException.class);
    }

    private Book bookWithStatus(AvailabilityStatus status) {
        var book = new Book();
        book.setTitle("Test Book");
        book.setAuthor("Author");
        book.setIsbn("9780132350884");
        book.setPublishedYear((short) 2008);
        book.setAvailabilityStatus(status);
        // Reflectively set id since it's normally set by JPA
        try {
            var field = Book.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(book, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return book;
    }
}
