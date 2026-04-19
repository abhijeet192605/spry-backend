package com.spry.library.wishlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.library.book.exception.BookNotFoundException;
import com.spry.library.config.TestSecurityConfig;
import com.spry.library.wishlist.dto.WishlistRequest;
import com.spry.library.wishlist.dto.WishlistResponse;
import com.spry.library.wishlist.exception.WishlistConflictException;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WishlistController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class WishlistControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  WishlistService wishlistService;

    private static final UUID BOOK_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ── POST /api/v1/wishlist ─────────────────────────────────────────────────

    @Test
    void addToWishlist_returns201WithLocation_whenValid() throws Exception {
        var req = new WishlistRequest(BOOK_ID, USER_ID);
        var response = new WishlistResponse(UUID.randomUUID(), BOOK_ID, "Clean Code", USER_ID, Instant.now());
        when(wishlistService.addToWishlist(eq(BOOK_ID), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(readerUser()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID.toString()))
                .andExpect(jsonPath("$.bookTitle").value("Clean Code"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void addToWishlist_returns401_whenUnauthenticated() throws Exception {
        var req = new WishlistRequest(BOOK_ID, USER_ID);

        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addToWishlist_returns400_whenBookIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(readerUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void addToWishlist_returns409_whenBookAlreadyInWishlist() throws Exception {
        var req = new WishlistRequest(BOOK_ID, USER_ID);
        when(wishlistService.addToWishlist(any(), any())).thenThrow(new WishlistConflictException(BOOK_ID));

        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(readerUser()))
                .andExpect(status().isConflict());
    }

    @Test
    void addToWishlist_returns404_whenBookNotFound() throws Exception {
        var req = new WishlistRequest(BOOK_ID, USER_ID);
        when(wishlistService.addToWishlist(any(), any())).thenThrow(new BookNotFoundException(BOOK_ID));

        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(readerUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void addToWishlist_returns201_whenCallerIsLibrarian() throws Exception {
        var req = new WishlistRequest(BOOK_ID, USER_ID);
        var response = new WishlistResponse(UUID.randomUUID(), BOOK_ID, "Clean Code", USER_ID, Instant.now());
        when(wishlistService.addToWishlist(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(librarianUser()))
                .andExpect(status().isCreated());
    }

    // ── DELETE /api/v1/wishlist/{bookId} ─────────────────────────────────────

    @Test
    void removeFromWishlist_returns204_whenValid() throws Exception {
        doNothing().when(wishlistService).removeFromWishlist(eq(BOOK_ID), eq(USER_ID));

        mockMvc.perform(delete("/api/v1/wishlist/{bookId}", BOOK_ID)
                        .param("userId", USER_ID.toString())
                        .with(readerUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeFromWishlist_returns204_whenEntryDoesNotExist() throws Exception {
        doNothing().when(wishlistService).removeFromWishlist(any(), any());

        mockMvc.perform(delete("/api/v1/wishlist/{bookId}", BOOK_ID)
                        .param("userId", USER_ID.toString())
                        .with(readerUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeFromWishlist_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/wishlist/{bookId}", BOOK_ID)
                        .param("userId", USER_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RequestPostProcessor readerUser() {
        return user(new JwtUser(USER_ID, "reader@spry.io", "READER"));
    }

    private RequestPostProcessor librarianUser() {
        return user(new JwtUser(USER_ID, "librarian@spry.io", "LIBRARIAN"));
    }
}
