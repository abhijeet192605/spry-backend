package com.spry.library.book;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.library.book.dto.BookFilterParams;
import com.spry.library.book.dto.BookMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final OutboxEventRepository outboxRepository;
    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public BookResponse create(CreateBookRequest req, UUID actorId) {
        if (bookRepository.existsByIsbn(req.isbn())) {
            throw new DuplicateIsbnException(req.isbn());
        }

        var book = bookMapper.toEntity(req);
        bookRepository.save(book);

        log.info("Book created: id={}, isbn={}, actor={}", book.getId(), book.getIsbn(), actorId);
        return bookMapper.toResponse(book);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> list(BookFilterParams filters, Pageable pageable) {
        return bookRepository
                .findWithFilters(filters.author(), filters.year(), filters.status(), pageable)
                .map(bookMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> search(String query, Pageable pageable) {
        return bookRepository.search(query.trim(), pageable).map(bookMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BookResponse getById(UUID id) {
        return bookMapper.toResponse(findActiveBook(id));
    }

    @Transactional
    public BookResponse update(UUID id, UpdateBookRequest req, UUID actorId) {
        var book = findActiveBook(id);

        if (!book.getIsbn().equals(req.isbn()) && bookRepository.existsByIsbnAndIdNot(req.isbn(), id)) {
            throw new DuplicateIsbnException(req.isbn());
        }

        bookMapper.updateEntity(req, book);
        log.info("Book updated: id={}, actor={}", id, actorId);
        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse updateStatus(UUID id, AvailabilityStatus newStatus, UUID actorId) {
        var book = findActiveBook(id);

        if (book.getAvailabilityStatus() == newStatus) {
            throw new StatusConflictException(
                    "Book " + id + " is already " + newStatus.name().toLowerCase());
        }

        var previousStatus = book.getAvailabilityStatus();
        book.setAvailabilityStatus(newStatus);

        // Only notify wishlist users when a book becomes available
        if (newStatus == AvailabilityStatus.AVAILABLE) {
            outboxRepository.save(buildStatusChangedEvent(book));
        }

        meterRegistry.counter("books.status.updated.total", "status", newStatus.name()).increment();
        log.info("Book status updated: id={}, from={}, to={}, actor={}", id, previousStatus, newStatus, actorId);

        return bookMapper.toResponse(book);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        var book = findActiveBook(id);

        if (book.getAvailabilityStatus() == AvailabilityStatus.BORROWED) {
            throw new BookNotDeletableException(id);
        }

        book.softDelete(actorId);
        log.info("Book soft-deleted: id={}, actor={}", id, actorId);
    }

    private Book findActiveBook(UUID id) {
        return bookRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    private OutboxEvent buildStatusChangedEvent(Book book) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "bookId", book.getId().toString(),
                    "bookTitle", book.getTitle(),
                    "newStatus", book.getAvailabilityStatus().name()
            ));
            return OutboxEvent.of(book.getId(), "BookStatusChanged", payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for book " + book.getId(), e);
        }
    }
}
