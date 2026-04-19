package com.spry.library.wishlist;

import com.spry.library.book.AvailabilityStatus;
import com.spry.library.book.Book;
import com.spry.library.book.BookRepository;
import com.spry.library.book.exception.BookNotFoundException;
import com.spry.library.user.User;
import com.spry.library.user.UserRepository;
import com.spry.library.user.UserRole;
import com.spry.library.wishlist.dto.WishlistMapper;
import com.spry.library.wishlist.dto.WishlistResponse;
import com.spry.library.wishlist.exception.WishlistConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock WishlistRepository wishlistRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock WishlistMapper wishlistMapper;

    WishlistService wishlistService;

    private final UUID userId = UUID.randomUUID();
    private final UUID bookId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(wishlistRepository, bookRepository, userRepository, wishlistMapper);
    }

    // ── addToWishlist ─────────────────────────────────────────────────────────

    @Test
    void addToWishlist_savesEntryAndReturnsResponse() {
        var book = bookWithId(bookId);
        var user = userWithId(userId);
        var expectedResponse = new WishlistResponse(UUID.randomUUID(), bookId, "Clean Code", userId, Instant.now());

        when(wishlistRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(false);
        when(bookRepository.findByIdAndDeletedFalse(bookId)).thenReturn(Optional.of(book));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(wishlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wishlistMapper.toResponse(any())).thenReturn(expectedResponse);

        var result = wishlistService.addToWishlist(bookId, userId);

        assertThat(result.bookId()).isEqualTo(bookId);
        assertThat(result.userId()).isEqualTo(userId);

        var captor = ArgumentCaptor.forClass(WishlistEntry.class);
        verify(wishlistRepository).save(captor.capture());
        assertThat(captor.getValue().getBook()).isEqualTo(book);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void addToWishlist_throwsConflict_whenBookAlreadyInWishlist() {
        when(wishlistRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(true);

        assertThatThrownBy(() -> wishlistService.addToWishlist(bookId, userId))
                .isInstanceOf(WishlistConflictException.class);

        verify(bookRepository, never()).findByIdAndDeletedFalse(any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void addToWishlist_throwsNotFound_whenBookDoesNotExist() {
        when(wishlistRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(false);
        when(bookRepository.findByIdAndDeletedFalse(bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addToWishlist(bookId, userId))
                .isInstanceOf(BookNotFoundException.class);

        verify(userRepository, never()).findById(any());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void addToWishlist_throwsIllegalState_whenAuthenticatedUserNotInDatabase() {
        when(wishlistRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(false);
        when(bookRepository.findByIdAndDeletedFalse(bookId)).thenReturn(Optional.of(bookWithId(bookId)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.addToWishlist(bookId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(userId.toString());

        verify(wishlistRepository, never()).save(any());
    }

    // ── removeFromWishlist ────────────────────────────────────────────────────

    @Test
    void removeFromWishlist_deletesEntry_whenEntryExists() {
        var entry = new WishlistEntry();
        when(wishlistRepository.findByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(entry));

        wishlistService.removeFromWishlist(bookId, userId);

        verify(wishlistRepository).delete(entry);
    }

    @Test
    void removeFromWishlist_doesNothing_whenEntryDoesNotExist() {
        when(wishlistRepository.findByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());

        wishlistService.removeFromWishlist(bookId, userId);

        verify(wishlistRepository, never()).delete(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Book bookWithId(UUID id) {
        var book = new Book();
        book.setTitle("Clean Code");
        book.setAuthor("Robert Martin");
        book.setIsbn("9780132350884");
        book.setPublishedYear((short) 2008);
        book.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        try {
            var field = Book.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(book, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return book;
    }

    private User userWithId(UUID id) {
        var user = new User();
        user.setRole(UserRole.READER);
        user.setEmail("reader@spry.io");
        user.setName("Test Reader");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
