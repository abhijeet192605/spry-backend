package com.spry.library.book;

import com.spry.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
public class Book extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 200)
    private String author;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(name = "published_year", nullable = false)
    private short publishedYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;
}
