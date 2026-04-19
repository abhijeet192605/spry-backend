# Library Management Service — Architecture

> **Service:** `library-service`  
> **Port:** `8081`  
> **Database:** `library_db` (PostgreSQL 15)  
> **Kafka Topics:** `book.status.changed`, `book.status.changed.DLQ`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [Technology Stack](#3-technology-stack)
4. [Domain Model](#4-domain-model)
5. [Database Schema](#5-database-schema)
6. [API Reference](#6-api-reference)
7. [Async Flow — Wishlist Notification](#7-async-flow--wishlist-notification)
8. [Kafka Design](#8-kafka-design)
9. [Security](#9-security)
10. [Observability](#10-observability)
11. [Failure Modes & Mitigations](#11-failure-modes--mitigations)
12. [Non-Functional Requirements](#12-non-functional-requirements)
13. [Key Design Decisions](#13-key-design-decisions)

---

## 1. Overview

The Library Management Service is a standalone Spring Boot microservice responsible for managing a library's book inventory. It supports full CRUD operations on books, partial-match search, wishlist management, and asynchronous user notifications when a borrowed book becomes available.

### Core Responsibilities

| Responsibility | Mechanism |
|---|---|
| Book inventory CRUD | REST API + PostgreSQL |
| Paginated book listing with filters | Spring Data JPA + Pageable |
| Partial-match search by title / author | JPQL ILIKE query |
| Availability status updates | PATCH endpoint + outbox event |
| Wishlist management per user | Junction table |
| Async wishlist notifications | Kafka consumer + notification_log |
| Soft delete with audit trail | `deleted` flag + `deleted_at` + `deleted_by` |

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        library-service                              │
│                                                                     │
│  REST Client                                                        │
│      │                                                              │
│      ▼                                                              │
│  ┌─────────────────┐     ┌──────────────────┐                       │
│  │  BookController  │────▶│   BookService    │                       │
│  │  WishlistCtrl    │     │  WishlistService │                       │
│  └─────────────────┘     └────────┬─────────┘                       │
│                                   │  @Transactional                 │
│                                   ▼                                 │
│                          ┌─────────────────┐                        │
│                          │   library_db     │  PostgreSQL 15         │
│                          │  (PostgreSQL)    │                        │
│                          └─────────────────┘                        │
│                                   │                                 │
│                          ┌────────▼────────┐                        │
│                          │ OutboxRelayService│  @Scheduled 500ms     │
│                          └────────┬────────┘                        │
│                                   │                                 │
└───────────────────────────────────┼─────────────────────────────────┘
                                    │ publish
                                    ▼
                         ┌──────────────────────┐
                         │  book.status.changed  │  Kafka Topic
                         └──────────┬───────────┘
                                    │ @KafkaListener
                                    ▼
                         ┌──────────────────────┐
                         │ NotificationConsumer  │  3 retries → DLQ
                         │  (same service)       │
                         └──────────────────────┘
                                    │ on failure × 3
                                    ▼
                         ┌──────────────────────┐
                         │ book.status.changed   │  Dead Letter Queue
                         │       .DLQ            │
                         └──────────────────────┘
```

---

## 3. Technology Stack

| Component | Technology | Version |
|---|---|---|
| Framework | Spring Boot | 3.2.x |
| Language | Java | 17 |
| Database | PostgreSQL | 15 |
| ORM | Spring Data JPA / Hibernate | 6.x |
| DB Migration | Liquibase | 4.x |
| Messaging | Apache Kafka | 3.x |
| Validation | Jakarta Bean Validation | 3.x |
| Observability | Micrometer + OpenTelemetry | — |
| Mapping | MapStruct | 1.5.x |
| Build | Maven | 3.x |

---

## 4. Domain Model

```
┌─────────────┐        ┌──────────────┐
│    USERS    │        │    BOOKS     │
│─────────────│        │──────────────│
│ id (PK)     │        │ id (PK)      │
│ name        │        │ title        │
│ email       │        │ author       │
│ role        │        │ isbn (UQ)    │
│ created_at  │        │ published_year│
└──────┬──────┘        │ availability │
       │               │  _status     │
       │               │ deleted      │
       │               │ deleted_at   │
       │               │ deleted_by   │
       │               │ created_at   │
       │               │ updated_at   │
       │               └──────┬───────┘
       │                      │
       └──────────┬───────────┘
                  │
         ┌────────▼──────────┐
         │     WISHLIST      │
         │───────────────────│
         │ id (PK)           │
         │ user_id (FK)      │
         │ book_id (FK)      │
         │ created_at        │
         │ UNIQUE(user_id,   │
         │        book_id)   │
         └───────────────────┘

┌────────────────────┐      ┌──────────────────────┐
│  NOTIFICATION_LOG  │      │    OUTBOX_EVENTS      │
│────────────────────│      │──────────────────────│
│ id (PK)            │      │ id (PK)              │
│ user_id (FK)       │      │ aggregate_id         │
│ book_id (FK)       │      │ event_type           │
│ kafka_event_id (UQ)│      │ payload (JSONB)      │
│ message            │      │ status               │
│ notified_at        │      │ created_at           │
└────────────────────┘      │ published_at         │
                            └──────────────────────┘
```

### Enums

| Enum | Values |
|---|---|
| `AvailabilityStatus` | `AVAILABLE`, `BORROWED` |
| `UserRole` | `READER`, `LIBRARIAN`, `ADMIN` |
| `OutboxStatus` | `PENDING`, `PUBLISHED`, `FAILED` |

---

## 5. Database Schema

### `books`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | `gen_random_uuid()` |
| `title` | `VARCHAR(300)` | NOT NULL | — |
| `author` | `VARCHAR(200)` | NOT NULL, IDX | Indexed for search |
| `isbn` | `VARCHAR(20)` | NOT NULL, UNIQUE | ISBN-13 format |
| `published_year` | `SMALLINT` | NOT NULL | CHECK (1000–current year) |
| `availability_status` | `VARCHAR(20)` | NOT NULL, IDX | DEFAULT `AVAILABLE` |
| `deleted` | `BOOLEAN` | NOT NULL | DEFAULT `false` |
| `deleted_at` | `TIMESTAMPTZ` | nullable | Set on soft delete |
| `deleted_by` | `UUID` | nullable | Actor who deleted |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | DEFAULT `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Auto-updated via JPA |

### `users`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `name` | `VARCHAR(200)` | NOT NULL | — |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Login identifier |
| `role` | `VARCHAR(20)` | NOT NULL | READER / LIBRARIAN / ADMIN |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |

### `wishlist`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `user_id` | `UUID` | FK → users.id, IDX | NOT NULL |
| `book_id` | `UUID` | FK → books.id, IDX | NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |

> **Constraint:** `UNIQUE(user_id, book_id)` — one wishlist entry per user per book.

### `notification_log`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `user_id` | `UUID` | FK → users.id, IDX | NOT NULL |
| `book_id` | `UUID` | FK → books.id, IDX | NOT NULL |
| `kafka_event_id` | `VARCHAR(100)` | NOT NULL, UNIQUE | Idempotency key (`topic-offset`) |
| `message` | `TEXT` | NOT NULL | Human-readable notification |
| `notified_at` | `TIMESTAMPTZ` | NOT NULL | DEFAULT `now()` |

> **Retention policy:** Purge records older than 90 days via scheduled job.

### `outbox_events`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `aggregate_id` | `UUID` | NOT NULL | The `book.id` that triggered the event |
| `event_type` | `VARCHAR(100)` | NOT NULL | e.g. `BookStatusChanged` |
| `payload` | `JSONB` | NOT NULL | Full event payload |
| `status` | `VARCHAR(20)` | NOT NULL, IDX | PENDING / PUBLISHED / FAILED |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |
| `published_at` | `TIMESTAMPTZ` | nullable | Set after successful Kafka publish |

### Indexes & Constraints Summary

```sql
-- Books
CREATE UNIQUE INDEX ON books (isbn);
CREATE INDEX ON books (author);
CREATE INDEX ON books (availability_status);
CHECK (published_year BETWEEN 1000 AND EXTRACT(YEAR FROM now()))

-- Wishlist
CREATE UNIQUE INDEX ON wishlist (user_id, book_id);
CREATE INDEX ON wishlist (book_id);       -- used by NotificationConsumer

-- Notification Log
CREATE UNIQUE INDEX ON notification_log (kafka_event_id);

-- Outbox
CREATE INDEX ON outbox_events (status) WHERE status = 'PENDING';  -- partial index
```

---

## 6. API Reference

**Base URL:** `/api/v1`  
**Content-Type:** `application/json`  
**Error Format:** RFC 7807 `application/problem+json`

---

### `POST /books`
Create a new book.

**Request:**
```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "9780132350884",
  "publishedYear": 2008
}
```

**Validation:**
- `title` — required, max 300 chars
- `author` — required, max 200 chars
- `isbn` — required, unique, ISBN-13 format
- `publishedYear` — required, between 1000 and current year

**Response `201 Created` + `Location: /api/v1/books/{id}`:**
```json
{
  "id": "a1b2c3d4-e5f6-...",
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "9780132350884",
  "publishedYear": 2008,
  "availabilityStatus": "AVAILABLE",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

**Errors:** `400` Validation failed · `401` Unauthorized · `409` ISBN already exists

---

### `GET /books`
Paginated list with optional filters.

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | int | No | Page number, default `0` |
| `size` | int | No | Page size, default `20`, max `100` |
| `author` | string | No | Partial match on author name |
| `year` | int | No | Exact match on published year |
| `status` | enum | No | `AVAILABLE` or `BORROWED` |
| `sort` | string | No | `title`, `author`, `publishedYear` |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "a1b2c3d4-...",
      "title": "Clean Code",
      "author": "Robert C. Martin",
      "isbn": "9780132350884",
      "publishedYear": 2008,
      "availabilityStatus": "AVAILABLE"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 142,
    "totalPages": 8
  }
}
```

---

### `GET /books/search`
Full partial-match search on title OR author.

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `q` | string | Yes | Min 2 chars — matches title OR author |
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |

**Response `200 OK`:** Same structure as `GET /books` with additional `"query": "clean"` field.

**Errors:** `400` Query too short (< 2 chars)

> **Design note:** Separate `/search` endpoint rather than a filter on `/books` — search has distinct semantics (multi-field matching, future relevance ranking) that don't belong in standard resource listing.

---

### `GET /books/{id}`
Get a single book by ID.

**Response `200 OK`:** Full book object (same as POST response).

**Errors:** `404` Not found or soft-deleted (callers cannot distinguish between the two)

---

### `PUT /books/{id}`
Full update of book metadata.

**Request:** Same fields as POST (all required for PUT).

> **Design note:** PUT is idempotent — all fields must be supplied. Availability status is intentionally excluded; use `PATCH /books/{id}/status` for that.

**Response `200 OK`:** Updated book object.

**Errors:** `400` Validation · `404` Not found · `409` ISBN conflict

---

### `PATCH /books/{id}/status`
Update availability status only. Triggers async wishlist notification when transitioning to `AVAILABLE`.

**Request:**
```json
{
  "status": "AVAILABLE"
}
```

**Response `200 OK`:**
```json
{
  "id": "a1b2c3d4-...",
  "title": "Clean Code",
  "availabilityStatus": "AVAILABLE",
  "updatedAt": "2024-01-15T11:00:00Z"
}
```

**Errors:** `400` Invalid status · `404` Not found · `409` Already in requested status

> **Design note:** PATCH (not PUT) signals a partial update on a single field. Business logic (Kafka publish, outbox insert) is triggered only from this specific endpoint — intentionally decoupled from general metadata edits.

---

### `DELETE /books/{id}`
Soft delete a book.

**Response `204 No Content`**

**Errors:** `404` Not found · `409` Book is currently `BORROWED`

> Soft delete sets `deleted = true`, `deleted_at`, `deleted_by`. All subsequent `GET` requests return `404`. Record is retained in DB for audit purposes.

---

### `POST /wishlist`
Add a book to a user's wishlist.

**Request:**
```json
{
  "bookId": "a1b2c3d4-...",
  "userId": "usr-uuid-..."
}
```

**Response `201 Created`:**
```json
{
  "id": "wl-uuid-...",
  "bookId": "a1b2c3d4-...",
  "bookTitle": "Clean Code",
  "userId": "usr-uuid-...",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `404` Book not found · `409` Already in wishlist

---

### `DELETE /wishlist/{bookId}`
Remove a book from a user's wishlist.

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | UUID | Yes | The user whose wishlist entry to remove |

**Response `204 No Content`**

> Idempotent — deleting a non-existent wishlist entry returns `204` (not `404`).

---

### Standard Error Response (RFC 7807)

```json
{
  "type": "https://api.spry.io/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "isbn: must match ISBN-13 format",
  "instance": "/api/v1/books",
  "traceId": "abc123def456",
  "errors": [
    { "field": "isbn", "message": "must match ISBN-13 format" }
  ]
}
```

---

## 7. Async Flow — Wishlist Notification

### Problem
When a borrowed book is returned (status changes to `AVAILABLE`), all users who wishlisted that book must be notified. This notification must **not block the API response**.

### Solution: Outbox Pattern + Kafka

```
PATCH /books/{id}/status
         │
         ▼
  ┌─────────────────────────────────────┐
  │  @Transactional                      │
  │  1. UPDATE books SET status=AVAILABLE│
  │  2. INSERT outbox_events (PENDING)   │  ← same transaction
  │  COMMIT                              │
  └────────────────────┬────────────────┘
                       │  returns 200 OK immediately
                       ▼
              OutboxRelayService
              @Scheduled(500ms)
                       │
                       │ KafkaTemplate.send("book.status.changed")
                       ▼
              ┌─────────────────────┐
              │ book.status.changed │  Kafka Topic
              └──────────┬──────────┘
                         │
                         ▼
              NotificationConsumer
              @KafkaListener
                         │
                         ├── SELECT wishlist WHERE book_id = ?
                         │
                         ├── for each user:
                         │      INSERT notification_log
                         │      (idempotency: skip if kafka_event_id exists)
                         │
                         └── log: "Notification prepared for user X"
```

### Why Outbox Pattern?

Without the outbox, if Kafka is unavailable at the moment of the PATCH call, the event would be lost — the DB is updated but no notification is ever sent. The outbox pattern stores the event in the DB atomically with the status update. The relay service publishes it when Kafka is available. **No event is ever silently lost.**

### Idempotency Key

The `NotificationConsumer` uses `topic + "-" + partition + "-" + offset` as the `kafka_event_id`. Before inserting any `notification_log` record, it checks `WHERE kafka_event_id = ?`. If the record already exists, the message is skipped. This ensures **exactly-once notification logging** even if Kafka delivers the message more than once.

---

## 8. Kafka Design

### Topics

| Topic | Partitions | Purpose |
|---|---|---|
| `book.status.changed` | 3 | Book available notifications |
| `book.status.changed.DLQ` | 3 | Failed messages after 3 retry attempts |

### Retry & DLQ Strategy

```
Kafka delivers message
        │
DefaultErrorHandler intercepts
        │
@KafkaListener (attempt 1) → FAIL
        │ wait 1s
@KafkaListener (attempt 2) → FAIL
        │ wait 2s
@KafkaListener (attempt 3) → FAIL
        │
DeadLetterPublishingRecoverer
        │
→ publish to book.status.changed.DLQ
→ commit offset (consumer moves on)
→ DLQ Handler logs alert
```

### DLQ Message Headers (added automatically by Spring)

| Header | Value |
|---|---|
| `kafka_dlt-original-topic` | `book.status.changed` |
| `kafka_dlt-original-offset` | `847` |
| `kafka_dlt-exception-message` | Exception message |
| `kafka_dlt-exception-stacktrace` | Full stack trace |

### Producer Config

| Setting | Value | Reason |
|---|---|---|
| `acks` | `all` | Wait for all replicas — no data loss |
| `retries` | `3` | Retry transient send failures |
| `enable.idempotence` | `true` | Exactly-once producer semantics |

---

## 9. Security

All endpoints are publicly accessible (no authentication required). Input validation is enforced via Jakarta Bean Validation on all request DTOs. No raw SQL — all queries use JPA / JPQL (SQL injection prevention).

---

## 10. Observability

### Health Checks

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Liveness — DB + Kafka connectivity |
| `GET /actuator/health/readiness` | Readiness — DB connection pool available |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |

### Key Metrics

| Metric | Type | Alert Condition |
|---|---|---|
| `books.status.updated.total` | Counter | — (informational) |
| `wishlist.notifications.sent.total` | Counter | — |
| `kafka.consumer.lag` | Gauge | Alert if lag > 1000 for > 5 min |
| `outbox.pending.count` | Gauge | Alert if > 100 PENDING for > 2 min |
| `http.server.requests` (p95) | Timer | Alert if p95 > 500ms |
| `notification.failures.total` | Counter | Alert if > 10 in 1 min |

### Structured Logging

Every log entry includes:
- `traceId` — OpenTelemetry trace ID (propagated to Kafka message headers)
- `spanId` — Current span
- `requestId` — Per-request correlation ID

---

## 11. Failure Modes & Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Kafka unavailable at publish time | Event not sent | Outbox pattern — event persisted in DB, relay retries until Kafka is up |
| Consumer crashes mid-batch | Messages not processed | Kafka offset not committed — messages redelivered on restart (idempotency ensures safe reprocessing) |
| Duplicate Kafka delivery | Duplicate notifications | `UNIQUE(kafka_event_id)` in `notification_log` prevents duplicate inserts |
| Consumer exhausts all retries | Message not processed | Routed to `book.status.changed.DLQ` — preserved for manual replay, alert fires |
| DB down during PATCH /status | Request fails | Transaction rolls back cleanly — no partial state; client retries |
| Book deleted while in wishlist | FK violation | FK constraint on `wishlist.book_id` — must remove wishlist entries before book can be deleted |

---

## 12. Non-Functional Requirements

| Requirement | Target |
|---|---|
| `PATCH /status` p95 response time | < 200ms |
| `GET /books` p95 response time | < 300ms |
| `GET /books/search` p95 response time | < 500ms (add `pg_trgm` GIN index beyond 100K books) |
| Notification delivery SLA | Best-effort, within 60s of status change |
| `notification_log` retention | 90 days |
| Service availability | 99.9% (≈ 45 min/month allowed downtime) |
| Max page size | 100 records |

---

## 13. Key Design Decisions

### Why PATCH for status update, not PUT?
`PUT` implies a full resource replacement — the client must send all fields. `PATCH` signals a partial update of a single field. More importantly, separating status updates from metadata edits allows the service to attach specific business logic (Kafka publish, outbox insert) only to status transitions — not to every book edit.

### Why Outbox Pattern instead of publishing directly to Kafka?
If Kafka is unavailable when the PATCH request arrives, a direct `kafkaTemplate.send()` would fail — and either the status update rolls back (inconsistent with user expectation) or the event is silently dropped. The outbox pattern writes the event to the DB in the same transaction as the status update, guaranteeing the event is never lost regardless of Kafka availability.

### Why soft delete instead of hard delete?
Soft delete (`deleted = true`) preserves the full audit trail — who deleted the book, when, and what state it was in. This is important for resolving disputes (e.g. "why was this book removed while I had it wishlisted?"). Hard deletes would also cascade-delete wishlist entries and notification logs, losing history permanently.

### Why a separate `/search` endpoint instead of a query param on `/books`?
Search has semantically different behaviour from filtering — it queries multiple fields simultaneously, may eventually support relevance ranking, and has different performance characteristics (full-text vs exact match). Separating the concern keeps each endpoint simple and allows them to evolve independently.

### Why `UNIQUE(kafka_event_id)` on `notification_log`?
Kafka guarantees **at-least-once** delivery — the same message can be delivered more than once (e.g. after a consumer restart). Without an idempotency key, a user could receive duplicate notification log entries for the same book availability event. The `UNIQUE` constraint on `kafka_event_id` makes the consumer idempotent at the database level — a second insert for the same event simply fails the unique check and is skipped.
