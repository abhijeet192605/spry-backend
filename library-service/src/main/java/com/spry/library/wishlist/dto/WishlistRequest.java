package com.spry.library.wishlist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WishlistRequest(
        @NotNull(message = "bookId is required")
        UUID bookId,

        @NotNull(message = "userId is required")
        UUID userId
) {}
