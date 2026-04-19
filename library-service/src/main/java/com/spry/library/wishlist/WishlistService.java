package com.spry.library.wishlist;

import com.spry.library.book.BookRepository;
import com.spry.library.book.exception.BookNotFoundException;
import com.spry.library.user.UserRepository;
import com.spry.library.wishlist.dto.WishlistMapper;
import com.spry.library.wishlist.dto.WishlistResponse;
import com.spry.library.wishlist.exception.WishlistConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final WishlistMapper wishlistMapper;

    @Transactional
    public WishlistResponse addToWishlist(UUID bookId, UUID userId) {
        if (wishlistRepository.existsByUserIdAndBookId(userId, bookId)) {
            throw new WishlistConflictException(bookId);
        }

        var book = bookRepository.findByIdAndDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB: " + userId));

        var entry = new WishlistEntry();
        entry.setUser(user);
        entry.setBook(book);
        wishlistRepository.save(entry);

        log.debug("Wishlist entry created: userId={}, bookId={}", userId, bookId);
        return wishlistMapper.toResponse(entry);
    }

    @Transactional
    public void removeFromWishlist(UUID bookId, UUID userId) {
        wishlistRepository.findByUserIdAndBookId(userId, bookId)
                .ifPresent(entry -> {
                    wishlistRepository.delete(entry);
                    log.debug("Wishlist entry removed: userId={}, bookId={}", userId, bookId);
                });
        // Idempotent — no error if the entry doesn't exist
    }
}
