package com.spry.library.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.library.book.dto.BookResponse;
import com.spry.library.book.dto.CreateBookRequest;
import com.spry.library.book.dto.UpdateBookRequest;
import com.spry.library.book.dto.UpdateStatusRequest;
import com.spry.library.book.exception.BookNotDeletableException;
import com.spry.library.book.exception.BookNotFoundException;
import com.spry.library.book.exception.DuplicateIsbnException;
import com.spry.library.book.exception.StatusConflictException;
import com.spry.library.config.TestSecurityConfig;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class BookControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  BookService bookService;

    private static final UUID BOOK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ── POST /api/v1/books ────────────────────────────────────────────────────

    @Test
    void createBook_returns201WithLocation_whenValid() throws Exception {
        var req = new CreateBookRequest("Clean Code", "Robert Martin", "9780132350884", 2008, null);
        when(bookService.create(any(), any())).thenReturn(bookResponse());

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.isbn").value("9780132350884"))
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"));
    }

    @Test
    void createBook_returns400_whenIsbnInvalid() throws Exception {
        var req = new CreateBookRequest("Title", "Author", "not-an-isbn", 2008, null);

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createBook_returns400_whenTitleBlank() throws Exception {
        var req = new CreateBookRequest("", "Author", "9780132350884", 2008, null);

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createBook_returns409_whenIsbnAlreadyExists() throws Exception {
        var req = new CreateBookRequest("Title", "Author", "9780132350884", 2008, null);
        when(bookService.create(any(), any())).thenThrow(new DuplicateIsbnException("9780132350884"));

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isConflict());
    }

@Test
    void createBook_returns401_whenUnauthenticated() throws Exception {
        var req = new CreateBookRequest("Title", "Author", "9780132350884", 2008, null);

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/books ────────────────────────────────────────────────────

    @Test
    void listBooks_returns200_withPagedResults() throws Exception {
        when(bookService.list(any(), any())).thenReturn(new PageImpl<>(List.of(bookResponse())));

        mockMvc.perform(get("/api/v1/books")
                        .with(readerUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].isbn").value("9780132350884"));
    }

    @Test
    void listBooks_returns200_withFilters() throws Exception {
        when(bookService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/books")
                        .param("author", "Martin")
                        .param("year", "2008")
                        .param("status", "AVAILABLE")
                        .with(readerUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listBooks_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/books/search ─────────────────────────────────────────────

    @Test
    void searchBooks_returns200_whenQueryValid() throws Exception {
        when(bookService.search(any(), any())).thenReturn(new PageImpl<>(List.of(bookResponse())));

        mockMvc.perform(get("/api/v1/books/search")
                        .param("q", "clean")
                        .with(readerUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void searchBooks_returns400_whenQueryTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/books/search")
                        .param("q", "a")
                        .with(readerUser()))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/books/{id} ───────────────────────────────────────────────

    @Test
    void getBook_returns200_whenFound() throws Exception {
        when(bookService.getById(BOOK_ID)).thenReturn(bookResponse());

        mockMvc.perform(get("/api/v1/books/{id}", BOOK_ID)
                        .with(readerUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOK_ID.toString()))
                .andExpect(jsonPath("$.title").value("Clean Code"));
    }

    @Test
    void getBook_returns404_whenNotFound() throws Exception {
        when(bookService.getById(BOOK_ID)).thenThrow(new BookNotFoundException(BOOK_ID));

        mockMvc.perform(get("/api/v1/books/{id}", BOOK_ID)
                        .with(readerUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBook_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/books/{id}", BOOK_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/books/{id} ───────────────────────────────────────────────

    @Test
    void updateBook_returns200_whenValid() throws Exception {
        var req = new UpdateBookRequest("Clean Code 2nd Ed", "Robert Martin", "9780132350884", 2020);
        when(bookService.update(eq(BOOK_ID), any(), any())).thenReturn(bookResponse());

        mockMvc.perform(put("/api/v1/books/{id}", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9780132350884"));
    }

    @Test
    void updateBook_returns400_whenIsbnInvalid() throws Exception {
        var req = new UpdateBookRequest("Title", "Author", "bad-isbn", 2020);

        mockMvc.perform(put("/api/v1/books/{id}", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void updateBook_returns404_whenNotFound() throws Exception {
        var req = new UpdateBookRequest("Title", "Author", "9780132350884", 2020);
        when(bookService.update(eq(BOOK_ID), any(), any())).thenThrow(new BookNotFoundException(BOOK_ID));

        mockMvc.perform(put("/api/v1/books/{id}", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBook_returns409_whenIsbnConflicts() throws Exception {
        var req = new UpdateBookRequest("Title", "Author", "9780596007126", 2005);
        when(bookService.update(eq(BOOK_ID), any(), any())).thenThrow(new DuplicateIsbnException("9780596007126"));

        mockMvc.perform(put("/api/v1/books/{id}", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isConflict());
    }

// ── PATCH /api/v1/books/{id}/status ─────────────────────────────────────

    @Test
    void updateStatus_returns200_whenValid() throws Exception {
        var req = new UpdateStatusRequest(AvailabilityStatus.AVAILABLE);
        when(bookService.updateStatus(eq(BOOK_ID), eq(AvailabilityStatus.AVAILABLE), any()))
                .thenReturn(bookResponse());

        mockMvc.perform(patch("/api/v1/books/{id}/status", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"));
    }

    @Test
    void updateStatus_returns409_whenAlreadyInStatus() throws Exception {
        var req = new UpdateStatusRequest(AvailabilityStatus.AVAILABLE);
        when(bookService.updateStatus(eq(BOOK_ID), any(), any()))
                .thenThrow(new StatusConflictException("Book is already AVAILABLE"));

        mockMvc.perform(patch("/api/v1/books/{id}/status", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns404_whenBookNotFound() throws Exception {
        var req = new UpdateStatusRequest(AvailabilityStatus.BORROWED);
        when(bookService.updateStatus(eq(BOOK_ID), any(), any()))
                .thenThrow(new BookNotFoundException(BOOK_ID));

        mockMvc.perform(patch("/api/v1/books/{id}/status", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isNotFound());
    }

@Test
    void updateStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/books/{id}/status", BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(librarianUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ── DELETE /api/v1/books/{id} ────────────────────────────────────────────

    @Test
    void deleteBook_returns204_whenAdminDeletesAvailableBook() throws Exception {
        mockMvc.perform(delete("/api/v1/books/{id}", BOOK_ID)
                        .with(adminUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBook_returns404_whenNotFound() throws Exception {
        doThrow(new BookNotFoundException(BOOK_ID))
                .when(bookService).delete(eq(BOOK_ID), any());

        mockMvc.perform(delete("/api/v1/books/{id}", BOOK_ID)
                        .with(adminUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_returns409_whenBookIsBorrowed() throws Exception {
        doThrow(new BookNotDeletableException(BOOK_ID))
                .when(bookService).delete(eq(BOOK_ID), any());

        mockMvc.perform(delete("/api/v1/books/{id}", BOOK_ID)
                        .with(adminUser()))
                .andExpect(status().isConflict());
    }

// ── Helpers ───────────────────────────────────────────────────────────────

    private BookResponse bookResponse() {
        return new BookResponse(BOOK_ID, "Clean Code", "Robert Martin",
                "9780132350884", 2008, AvailabilityStatus.AVAILABLE,
                Instant.now(), Instant.now());
    }

        private RequestPostProcessor librarianUser() {
        return user(new JwtUser(USER_ID, "librarian@spry.io", "LIBRARIAN"));
    }

        private RequestPostProcessor readerUser() {
        return user(new JwtUser(USER_ID, "reader@spry.io", "READER"));
    }

        private RequestPostProcessor adminUser() {
        return user(new JwtUser(USER_ID, "admin@spry.io", "ADMIN"));
    }
}
