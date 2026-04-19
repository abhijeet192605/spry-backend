package com.spry.library.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {

    Optional<Book> findByIdAndDeletedFalse(UUID id);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, UUID id);

    @Query("""
            SELECT b FROM Book b
            WHERE b.deleted = false
              AND (:author IS NULL OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%')))
              AND (:year IS NULL OR b.publishedYear = :year)
              AND (:status IS NULL OR b.availabilityStatus = :status)
            """)
    Page<Book> findWithFilters(
            @Param("author") String author,
            @Param("year") Integer year,
            @Param("status") AvailabilityStatus status,
            Pageable pageable);

    @Query("""
            SELECT b FROM Book b
            WHERE b.deleted = false
              AND (LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(b.author) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Book> search(@Param("q") String query, Pageable pageable);
}
