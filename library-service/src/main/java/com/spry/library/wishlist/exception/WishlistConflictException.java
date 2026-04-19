package com.spry.library.wishlist.exception;

import com.spry.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WishlistConflictException extends ApiException {

    public WishlistConflictException(UUID bookId) {
        super("Book " + bookId + " is already in your wishlist");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
