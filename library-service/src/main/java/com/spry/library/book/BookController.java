package com.spry.library.book;

import com.spry.library.book.dto.BookFilterParams;
import com.spry.library.book.dto.BookResponse;
import com.spry.library.book.dto.CreateBookRequest;
import com.spry.library.book.dto.UpdateBookRequest;
import com.spry.library.book.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Validated
public class BookController {

    private final BookService bookService;

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest req) {
        var book = bookService.create(req, null);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(book.id()).toUri();
        return ResponseEntity.created(location).body(book);
    }

    @GetMapping
    public Page<BookResponse> list(
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) AvailabilityStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return bookService.list(BookFilterParams.of(author, year, status), pageable);
    }

    @GetMapping("/search")
    public Page<BookResponse> search(
            @RequestParam @Size(min = 2, message = "Search query must be at least 2 characters") String q,
            @PageableDefault(size = 20) Pageable pageable) {

        return bookService.search(q, pageable);
    }

    @GetMapping("/{id}")
    public BookResponse getById(@PathVariable UUID id) {
        return bookService.getById(id);
    }

    @PutMapping("/{id}")
    public BookResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBookRequest req) {

        return bookService.update(id, req, null);
    }

    @PatchMapping("/{id}/status")
    public BookResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req) {

        return bookService.updateStatus(id, req.status(), null);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bookService.delete(id, null);
        return ResponseEntity.noContent().build();
    }
}
