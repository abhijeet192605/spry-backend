package com.spry.library.book.dto;

import com.spry.library.book.AvailabilityStatus;
import com.spry.library.book.Book;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "availabilityStatus",
             expression = "java(req.availabilityStatus() != null ? req.availabilityStatus() : AvailabilityStatus.AVAILABLE)")
    Book toEntity(CreateBookRequest req);

    BookResponse toResponse(Book book);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "availabilityStatus", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    void updateEntity(UpdateBookRequest req, @MappingTarget Book book);
}
