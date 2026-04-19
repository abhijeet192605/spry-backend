# Library Service — Implementation Plan

> **Service:** `library-service` | **Port:** `8081`  
> **Database:** `library_db` (PostgreSQL 15) | **Kafka:** `book.status.changed`, `book.status.changed.DLQ`

---

## Table of Contents

1. [Phase 1 — Project Scaffold & Shared Lib](#1-phase-1--project-scaffold--shared-lib)
2. [Phase 2 — Database & Migrations](#2-phase-2--database--migrations)
3. [Phase 3 — Domain & Persistence Layer](#3-phase-3--domain--persistence-layer)
4. [Phase 4 — Service Layer](#4-phase-4--service-layer)
5. [Phase 5 — REST Controllers](#5-phase-5--rest-controllers)
6. [Phase 6 — Kafka & Outbox Pattern](#6-phase-6--kafka--outbox-pattern)
7. [Phase 7 — Security](#7-phase-7--security)
8. [Phase 8 — Observability](#8-phase-8--observability)
9. [Phase 9 — Testing](#9-phase-9--testing)
10. [Checklist Summary](#10-checklist-summary)

---

## 1. Phase 1 — Project Scaffold & Shared Lib

### Tasks

- [ ] Create Maven multi-module parent `pom.xml` under `spry-backend/`
- [ ] Define shared dependency versions (Spring Boot 3.2.x, Java 17, Liquibase 4.x, jjwt 0.12.x, MapStruct 1.5.x, Micrometer)
- [ ] Create `shared-lib` module with the following:
  - `AuditEntity` base class: `createdAt`, `updatedAt` (`@PrePersist`, `@PreUpdate`)
  - `SoftDeletableEntity` base class: `deleted`, `deletedAt`, `deletedBy`
  - `JwtAuthFilter`: extracts `sub` claim → populates `SecurityContext`
  - `JwtService`: RS256 token validation
  - `GlobalExceptionHandler` (`@RestControllerAdvice`): RFC 7807 error responses
  - `ProblemDetail` builder utilities
- [ ] Create `library-service` Maven module, add parent reference
- [ ] Configure `application.yml`: port `8081`, datasource (`library_db`), Kafka bootstrap
- [ ] Add Docker Compose with PostgreSQL (`library_db`), Kafka, Zookeeper

### Key Files

```
shared-lib/src/main/java/com/spry/shared/
  ├── entity/AuditEntity.java
  ├── entity/SoftDeletableEntity.java
  ├── security/JwtAuthFilter.java
  ├── security/JwtService.java
  └── exception/GlobalExceptionHandler.java

library-service/src/main/resources/application.yml
```

---

## 2. Phase 2 — Database & Migrations

### Tasks

- [ ] Add Liquibase dependency and `db/changelog/db.changelog-master.yaml`
- [ ] Create migration `V001__create_users.sql`
  - `id UUID PK`, `name VARCHAR(200) NOT NULL`, `email VARCHAR(255) NOT NULL UNIQUE`, `role VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- [ ] Create migration `V002__create_books.sql`
  - All columns per schema spec (UUID PK, title, author, isbn UNIQUE, published_year CHECK 1000–current, availability_status DEFAULT `AVAILABLE`, soft-delete fields, audit fields)
  - Indexes: `isbn` (unique), `author`, `availability_status`
- [ ] Create migration `V003__create_wishlist.sql`
  - `id UUID PK`, `user_id FK → users.id`, `book_id FK → books.id`, `created_at`
  - `UNIQUE(user_id, book_id)` constraint
  - Index on `book_id` (used by NotificationConsumer)
- [ ] Create migration `V004__create_notification_log.sql`
  - `kafka_event_id VARCHAR(100) NOT NULL UNIQUE` — idempotency key
- [ ] Create migration `V005__create_outbox_events.sql`
  - `status` partial index `WHERE status = 'PENDING'`

### Enums to Register

| Enum | Values |
|---|---|
| `AvailabilityStatus` | `AVAILABLE`, `BORROWED` |
| `UserRole` | `READER`, `LIBRARIAN`, `ADMIN` |
| `OutboxStatus` | `PENDING`, `PUBLISHED`, `FAILED` |

---

## 3. Phase 3 — Domain & Persistence Layer

### Tasks

- [ ] Create `Book` entity (`@Entity`, extends `SoftDeletableEntity`)
  - `@Column(unique = true)` on `isbn`
  - `@Enumerated(EnumType.STRING)` on `availabilityStatus`
  - `@PreUpdate` to set `updatedAt`
- [ ] Create `User` entity
- [ ] Create `WishlistEntry` entity with `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))`
- [ ] Create `NotificationLog` entity with `@Column(unique = true)` on `kafkaEventId`
- [ ] Create `OutboxEvent` entity
- [ ] Create repositories:
  - `BookRepository`: `findByIdAndDeletedFalse()`, `findAllByDeletedFalse(Pageable)`, `existsByIsbnAndIdNot()`, custom JPQL search
  - `WishlistRepository`: `findByUserIdAndBookId()`, `findAllByBookId()`
  - `NotificationLogRepository`: `existsByKafkaEventId()`
  - `OutboxEventRepository`: `findByStatusOrderByCreatedAtAsc(OutboxStatus, Pageable)`

### Search Query (BookRepository)

```java
@Query("SELECT b FROM Book b WHERE b.deleted = false AND " +
       "(LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
       " LOWER(b.author) LIKE LOWER(CONCAT('%', :q, '%')))")
Page<Book> search(@Param("q") String query, Pageable pageable);
```

---

## 4. Phase 4 — Service Layer

### BookService

- [ ] `createBook(CreateBookRequest, UUID userId)` — validate ISBN uniqueness, save, return DTO
- [ ] `getBooks(BookFilterRequest, Pageable)` — apply author/year/status filters, exclude deleted
- [ ] `searchBooks(String q, Pageable)` — min 2 chars validation, delegate to repository
- [ ] `getBookById(UUID id)` — throw `404` if deleted
- [ ] `updateBook(UUID id, UpdateBookRequest, UUID userId)` — PUT semantics: all fields required; ISBN conflict check
- [ ] `updateStatus(UUID id, AvailabilityStatus newStatus, UUID actorId)`:
  - Validate transition (reject if already in requested status → `409`)
  - `@Transactional`: UPDATE book + INSERT `outbox_events` (PENDING) in single transaction
  - Return updated DTO
- [ ] `deleteBook(UUID id, UUID actorId)`:
  - Reject if `status == BORROWED` → `409`
  - Soft delete: set `deleted=true`, `deletedAt`, `deletedBy`

### WishlistService

- [ ] `addToWishlist(UUID bookId, UUID userId)` — verify book exists; duplicate → `409`
- [ ] `removeFromWishlist(UUID bookId, UUID userId)` — idempotent: return `204` even if not found

### DTOs & Mappers

- [ ] `CreateBookRequest`, `UpdateBookRequest`, `BookResponse`, `BookSummaryResponse`
- [ ] `WishlistRequest`, `WishlistResponse`
- [ ] `UpdateStatusRequest`
- [ ] `BookMapper` (MapStruct): entity ↔ DTO
- [ ] `WishlistMapper` (MapStruct)

---

## 5. Phase 5 — REST Controllers

### BookController (`/api/v1/books`)

- [ ] `POST /books` → `201 Created` + `Location` header
- [ ] `GET /books` → `200 OK` paginated with filters
- [ ] `GET /books/search?q=` → `200 OK` paginated; `400` if `q` < 2 chars
- [ ] `GET /books/{id}` → `200 OK` or `404`
- [ ] `PUT /books/{id}` → `200 OK`
- [ ] `PATCH /books/{id}/status` → `200 OK`
- [ ] `DELETE /books/{id}` → `204 No Content`

### WishlistController (`/api/v1/wishlist`)

- [ ] `POST /wishlist` → `201 Created`
- [ ] `DELETE /wishlist/{bookId}` → `204 No Content`

### Validation Annotations

| Field | Constraint |
|---|---|
| `title` | `@NotBlank @Size(max=300)` |
| `author` | `@NotBlank @Size(max=200)` |
| `isbn` | `@NotBlank @Pattern(ISBN-13 regex)` |
| `publishedYear` | `@NotNull @Min(1000) @Max(currentYear)` |
| `q` (search) | `@Size(min=2)` |

---

## 6. Phase 6 — Kafka & Outbox Pattern

### OutboxRelayService

- [ ] `@Scheduled(fixedDelay = 500)` — poll `outbox_events` WHERE `status = PENDING` (batch of 50)
- [ ] For each event: `KafkaTemplate.send("book.status.changed", payload)`
- [ ] On success: UPDATE `status = PUBLISHED`, set `publishedAt`
- [ ] On failure: UPDATE `status = FAILED`; log error

### Kafka Producer Config

```yaml
spring.kafka.producer:
  acks: all
  retries: 3
  properties:
    enable.idempotence: true
```

### NotificationConsumer

- [ ] `@KafkaListener(topics = "book.status.changed", groupId = "library-notifications")`
- [ ] Extract `kafka_event_id = topic + "-" + partition + "-" + offset`
- [ ] Skip if `notification_log` already has this `kafka_event_id` (idempotency)
- [ ] `SELECT wishlist WHERE book_id = ?`
- [ ] For each user: `INSERT notification_log` (wrapped in `@Transactional`)
- [ ] Log: `"Notification prepared for user {userId} for book {bookId}"`

### DLQ & Retry Config

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (r, e) -> new TopicPartition(r.topic() + ".DLQ", r.partition()));
    var backoff = new FixedBackOff(1000L, 3L); // 3 retries, 1s intervals
    return new DefaultErrorHandler(recoverer, backoff);
}
```

- [ ] `@KafkaListener` on `book.status.changed.DLQ` — log alert, metrics counter increment

### Notification Log Retention

- [ ] `@Scheduled(cron = "0 0 2 * * *")` — delete `notification_log` WHERE `notified_at < now() - 90 days`

---

## 7. Phase 7 — Security

### Spring Security Config

- [ ] `SecurityFilterChain`: add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- [ ] Permit `/actuator/**` without auth
- [ ] All `/api/v1/**` require authentication

### Role Guards (Method-level or `@PreAuthorize`)

| Endpoint | Allowed Roles |
|---|---|
| `POST /books`, `PUT /books/{id}`, `PATCH /books/{id}/status` | `LIBRARIAN`, `ADMIN` |
| `DELETE /books/{id}` | `ADMIN` |
| `GET /books/**`, `POST /wishlist`, `DELETE /wishlist/{bookId}` | All authenticated |

### Security Invariants to Implement

- [ ] `userId` always from JWT `sub` — never from request body
- [ ] Wishlist operations scoped to authenticated user only
- [ ] Email masked in all log statements (`user@***.com`)

---

## 8. Phase 8 — Observability

### Micrometer Metrics to Register

- [ ] `Counter`: `books.status.updated.total` — tag: `{status: AVAILABLE|BORROWED}`
- [ ] `Counter`: `wishlist.notifications.sent.total`
- [ ] `Counter`: `notification.failures.total` — alert if > 10 in 1 min
- [ ] `Gauge`: `outbox.pending.count` — query `COUNT(*) WHERE status=PENDING`
- [ ] `Gauge`: `kafka.consumer.lag` (via Micrometer Kafka binder)

### Actuator Endpoints

```yaml
management:
  endpoints.web.exposure.include: health, prometheus
  endpoint.health.show-details: always
  health.kafka.enabled: true
```

### Structured Logging (Logback + MDC)

- [ ] `JwtAuthFilter` sets MDC: `userId`, `requestId`
- [ ] Kafka consumer sets MDC: `traceId`, `spanId` from message headers
- [ ] Log pattern includes: `traceId`, `spanId`, `userId`, `requestId`

---

## 9. Phase 9 — Testing

### Unit Tests (Mockito)

- [ ] `BookServiceTest` — create, update, delete, status transition, ISBN conflict
- [ ] `WishlistServiceTest` — add, remove, duplicate prevention
- [ ] `NotificationConsumerTest` — idempotency (skip duplicate `kafka_event_id`)
- [ ] `OutboxRelayServiceTest` — publishes PENDING events, marks PUBLISHED

### Integration Tests (`@SpringBootTest` + MockMvc + Testcontainers)

- [ ] `BookControllerIT` — all 7 endpoints, auth, role enforcement
- [ ] `WishlistControllerIT` — add, remove, auth scoping
- [ ] Full PATCH `/status` → outbox insert → relay → Kafka publish flow

### Embedded Kafka Tests

- [ ] `NotificationConsumerKafkaTest` — happy path: produces event → consumer inserts `notification_log`
- [ ] Idempotency test — produce same event twice → only 1 `notification_log` row

### Concurrency / Edge Cases

- [ ] ISBN conflict on concurrent POST (test with `@Transactional` rollback)
- [ ] Soft delete + GET returns `404`
- [ ] Search with `q` < 2 chars → `400`

### Coverage Targets

| Layer | Target |
|---|---|
| Service | 80%+ line coverage |
| Controllers | All endpoints + error paths |
| Kafka Consumer | Happy path + idempotency |

---

## 10. Checklist Summary

- [ ] Maven multi-module setup + shared-lib
- [ ] Docker Compose (postgres, kafka, zookeeper)
- [ ] Liquibase migrations (all 5 tables)
- [ ] JPA entities + repositories
- [ ] BookService (CRUD, filters, search, soft delete)
- [ ] WishlistService
- [ ] DTOs + MapStruct mappers
- [ ] REST controllers with validation
- [ ] RFC 7807 GlobalExceptionHandler
- [ ] JWT filter + security config
- [ ] `OutboxRelayService` (`@Scheduled` 500ms)
- [ ] Kafka producer config (acks=all, idempotent)
- [ ] `NotificationConsumer` + idempotency
- [ ] DLQ + retry config (3 retries, 1s backoff)
- [ ] DLQ alert consumer
- [ ] Notification log retention scheduler
- [ ] Micrometer metrics
- [ ] Actuator health + Prometheus
- [ ] Structured logging (MDC)
- [ ] Unit + Integration + Kafka tests
- [ ] Test coverage ≥ 80% on service layer
