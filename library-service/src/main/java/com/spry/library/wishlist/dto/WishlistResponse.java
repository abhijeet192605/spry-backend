package com.spry.library.wishlist.dto;

import java.time.Instant;
import java.util.UUID;

public record WishlistResponse(
        UUID id,
        UUID bookId,
        String bookTitle,
        UUID userId,
        Instant createdAt
) {}
