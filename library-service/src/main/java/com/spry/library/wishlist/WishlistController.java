package com.spry.library.wishlist;

import com.spry.library.wishlist.dto.WishlistRequest;
import com.spry.library.wishlist.dto.WishlistResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping
    public ResponseEntity<WishlistResponse> add(@Valid @RequestBody WishlistRequest req) {
        var entry = wishlistService.addToWishlist(req.bookId(), req.userId());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{bookId}").buildAndExpand(req.bookId()).toUri();
        return ResponseEntity.created(location).body(entry);
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID bookId,
            @RequestParam UUID userId) {

        wishlistService.removeFromWishlist(bookId, userId);
        return ResponseEntity.noContent().build();
    }
}
