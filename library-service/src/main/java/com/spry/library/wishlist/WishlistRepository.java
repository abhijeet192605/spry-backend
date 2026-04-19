package com.spry.library.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository extends JpaRepository<WishlistEntry, UUID> {

    Optional<WishlistEntry> findByUserIdAndBookId(UUID userId, UUID bookId);

    List<WishlistEntry> findAllByBookId(UUID bookId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);
}
