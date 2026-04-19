package com.spry.library.wishlist.dto;

import com.spry.library.wishlist.WishlistEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WishlistMapper {

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "userId", source = "user.id")
    WishlistResponse toResponse(WishlistEntry entry);
}
