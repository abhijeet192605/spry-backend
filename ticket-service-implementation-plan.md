# Ticket Service — Implementation Plan

> **Service:** `ticket-service` | **Port:** `8083`  
> **Database:** `ticket_db` (PostgreSQL 15) | **Kafka:** `seat.hold.created`, `booking.confirmed`

---

## Table of Contents

1. [Phase 1 — Project Scaffold](#1-phase-1--project-scaffold)
2. [Phase 2 — Database & Migrations](#2-phase-2--database--migrations)
3. [Phase 3 — Domain & Persistence Layer](#3-phase-3--domain--persistence-layer)
4. [Phase 4 — Service Layer](#4-phase-4--service-layer)
5. [Phase 5 — REST Controllers](#5-phase-5--rest-controllers)
6. [Phase 6 — Concurrency & Overbooking Prevention](#6-phase-6--concurrency--overbooking-prevention)
7. [Phase 7 — Hold Expiry Scheduler](#7-phase-7--hold-expiry-scheduler)
8. [Phase 8 — Kafka (Analytics Events)](#8-phase-8--kafka-analytics-events)
9. [Phase 9 — Security](#9-phase-9--security)
10. [Phase 10 — Observability](#10-phase-10--observability)
11. [Phase 11 — Testing](#11-phase-11--testing)
12. [Checklist Summary](#12-checklist-summary)

---

## 1. Phase 1 — Project Scaffold

### Tasks

- [ ] Create `ticket-service` Maven module under `spry-backend/`; reference parent POM
- [ ] Add `shared-lib` dependency for `AuditEntity`, `SoftDeletableEntity`, `JwtAuthFilter`, `GlobalExceptionHandler`
- [ ] Add ShedLock 5.x dependency for distributed scheduler locking
- [ ] Configure `application.yml`: port `8083`, datasource (`ticket_db`), Kafka bootstrap
- [ ] Environment variable config:
  - `HOLD_TTL_MINUTES` (default `5`) — seat hold expiry duration
  - `MAX_SEATS_PER_HOLD` (default `10`) — max seats per hold request
- [ ] Add `ticket_db` to Docker Compose

---

## 2. Phase 2 — Database & Migrations

### Tasks

- [ ] `V001__create_users.sql`
  - `id UUID PK`, `name VARCHAR(200) NOT NULL`, `email VARCHAR(255) NOT NULL UNIQUE`, `created_at TIMESTAMPTZ NOT NULL`
- [ ] `V002__create_events.sql`
  - `id UUID PK DEFAULT gen_random_uuid()`, `name VARCHAR(300) NOT NULL`, `event_date TIMESTAMPTZ NOT NULL`
  - `location VARCHAR(400) NOT NULL`, `total_seats INTEGER NOT NULL CHECK (total_seats > 0)`
  - Audit fields: `created_at`, `updated_at`
  - Note: `total_seats` is immutable after creation — enforced at service layer
- [ ] `V003__create_seat_holds.sql`
  - `id UUID PK`, `event_id UUID FK → events.id`, `user_id UUID FK → users.id`
  - `seat_count INTEGER NOT NULL CHECK (seat_count >= 1)`
  - `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
  - `expires_at TIMESTAMPTZ NOT NULL`
  - Composite index: `CREATE INDEX ON seat_holds (event_id, status, expires_at)` — supports availability + expiry queries
  - Partial UNIQUE index (one active hold per user per event):
    ```sql
    CREATE UNIQUE INDEX idx_seat_holds_one_active_per_user_event
    ON seat_holds (user_id, event_id)
    WHERE status = 'ACTIVE';
    ```
- [ ] `V004__create_bookings.sql`
  - `id UUID PK`, `hold_id UUID FK → seat_holds.id UNIQUE` (1:1), `event_id UUID FK → events.id`
  - `user_id UUID FK → users.id`, `seat_count INTEGER NOT NULL`
  - `status VARCHAR(20) NOT NULL` (CONFIRMED / CANCELLED)
  - Soft-delete fields: `deleted BOOLEAN DEFAULT false`, `deleted_at`, `deleted_by`
  - `CREATE UNIQUE INDEX ON bookings (hold_id)` — prevents double-confirm
  - Partial UNIQUE index (no double booking per user per event):
    ```sql
    CREATE UNIQUE INDEX idx_bookings_no_double_booking
    ON bookings (user_id, event_id)
    WHERE deleted = false;
    ```
- [ ] `V005__create_shedlock.sql`
  - Standard ShedLock table for distributed lock on `HoldExpiryScheduler`

### Enums

| Enum | Values |
|---|---|
| `HoldStatus` | `ACTIVE`, `CONFIRMED`, `EXPIRED` |
| `BookingStatus` | `CONFIRMED`, `CANCELLED` |

### Hold Lifecycle

```
POST /holds → ACTIVE ──── expires in 5 min ────▶ EXPIRED
                │
                │  POST /bookings/confirm (within 5 min)
                ▼
           CONFIRMED ──── creates ────▶ BOOKING (CONFIRMED)
```

---

## 3. Phase 3 — Domain & Persistence Layer

### Entities

- [ ] `Event` entity (extends `AuditEntity`):
  - `totalSeats` field — no setter exposed; immutability enforced at service layer
- [ ] `User` entity
- [ ] `SeatHold` entity:
  - `@Enumerated(EnumType.STRING) private HoldStatus status`
  - `expiresAt` set on creation: `Instant.now().plus(holdTtlMinutes, ChronoUnit.MINUTES)`
- [ ] `Booking` entity (extends `SoftDeletableEntity`):
  - `@OneToOne private SeatHold hold`

### Repositories

- [ ] `EventRepository`:
  - `findById(UUID id)` standard
  - `findByIdWithLock(UUID id)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` for hold creation
- [ ] `SeatHoldRepository`:
  - `findByIdAndUserId(UUID id, UUID userId)` — ownership check on confirm
  - `findActiveHoldsByEventId(UUID eventId)` — for availability query
  - `updateExpiredHolds()` — batch UPDATE for expiry scheduler
- [ ] `BookingRepository`:
  - `findByIdAndDeletedFalse(UUID id)`
  - `existsByHoldId(UUID holdId)` — double-confirm guard

### Availability Query (Native SQL in Repository)

```sql
SELECT
  e.total_seats
  - COALESCE((SELECT SUM(b.seat_count) FROM bookings b
              WHERE b.event_id = :eventId AND b.status = 'CONFIRMED' AND b.deleted = false), 0)
  - COALESCE((SELECT SUM(h.seat_count) FROM seat_holds h
              WHERE h.event_id = :eventId AND h.status = 'ACTIVE' AND h.expires_at > now()), 0)
  AS available_seats
FROM events e
WHERE e.id = :eventId
```

---

## 4. Phase 4 — Service Layer

### EventService

- [ ] `createEvent(CreateEventRequest, UUID actorId)`:
  - Validate `eventDate` is in the future
  - Save and return DTO
- [ ] `getEvent(UUID id)` — standard fetch
- [ ] `getAvailability(UUID id)`:
  - Run availability query
  - Return `AvailabilityResponse` with `totalSeats`, `confirmedBookings`, `activeHolds`, `availableSeats`, `computedAt`

### HoldService

- [ ] `createHold(CreateHoldRequest, UUID userId)`:
  - Validate event exists and has not passed
  - Validate `seatCount` between 1 and `MAX_SEATS_PER_HOLD`
  - `@Transactional`:
    1. `eventRepository.findByIdWithLock(eventId)` — `SELECT FOR UPDATE`
    2. Run availability query
    3. If `availableSeats < seatCount` → throw `409 Insufficient seats`
    4. `INSERT seat_holds` with `expires_at = now() + HOLD_TTL_MINUTES`
  - Publish `seat.hold.created` to Kafka (fire-and-forget, analytics only)
  - Return `201` with hold details + `expiresAt`

### BookingService

- [ ] `confirmHold(ConfirmHoldRequest, UUID userId)`:
  - Load hold: `findByIdAndUserId` → `404` if not found, `403` if wrong user
  - If `status != ACTIVE` → `409 Conflict`
  - If `expires_at <= now()` → `410 Gone` (hold expired)
  - `@Transactional`:
    1. `INSERT bookings` (seat_count snapshot from hold)
    2. `UPDATE seat_holds SET status = CONFIRMED`
  - Unique constraint on `hold_id` in bookings prevents double-confirm race condition
  - Publish `booking.confirmed` to Kafka (analytics)
  - Return `201` with booking details
- [ ] `getBooking(UUID id, UUID userId, UserRole role)` — ownership check
- [ ] `cancelBooking(UUID id, UUID userId, UserRole role)`:
  - Ownership check
  - If event has already passed → `409`
  - Soft delete: `status = CANCELLED`, `deleted = true`, `deletedAt`, `deletedBy`

### DTOs & Mappers

- [ ] `CreateEventRequest`, `EventResponse`, `AvailabilityResponse`
- [ ] `CreateHoldRequest`, `HoldResponse`
- [ ] `ConfirmHoldRequest`, `BookingResponse`
- [ ] `EventMapper`, `HoldMapper`, `BookingMapper` (MapStruct)

---

## 5. Phase 5 — REST Controllers

### EventController (`/api/v1/events`)

- [ ] `POST /events` → `201 Created` (ADMIN only)
- [ ] `GET /events/{id}` → `200 OK`
- [ ] `GET /events/{id}/availability` → `200 OK`

### HoldController (`/api/v1/holds`)

- [ ] `POST /holds` → `201 Created` or `400`/`404`/`409`

### BookingController (`/api/v1/bookings`)

- [ ] `POST /bookings/confirm` → `201 Created` or `403`/`404`/`409`/`410`
- [ ] `GET /bookings/{id}` → `200 OK` or `403`/`404`
- [ ] `DELETE /bookings/{id}` → `204 No Content` or `403`/`404`/`409`

### Validation Annotations

| Field | Constraint |
|---|---|
| `name` | `@NotBlank @Size(max=300)` |
| `eventDate` | `@NotNull @Future` |
| `location` | `@NotBlank @Size(max=400)` |
| `totalSeats` | `@NotNull @Min(1)` |
| `eventId` | `@NotNull` |
| `seatCount` | `@NotNull @Min(1) @Max(${MAX_SEATS_PER_HOLD:10})` |
| `holdId` | `@NotNull` |

### Custom Error Responses

- [ ] Map `410 Gone` for expired hold using `ResponseEntityExceptionHandler`
- [ ] RFC 7807 body for `410`:
  ```json
  {
    "type": "https://api.spry.io/errors/resource-expired",
    "title": "Resource Expired",
    "status": 410,
    "detail": "Hold expired at {expiresAt}. Please create a new hold."
  }
  ```

---

## 6. Phase 6 — Concurrency & Overbooking Prevention

### SELECT FOR UPDATE Implementation

```java
// EventRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Event e WHERE e.id = :id")
Optional<Event> findByIdWithLock(@Param("id") UUID id);
```

### Why Pessimistic Lock Here

- High conflict rate on popular events — many users compete simultaneously
- Check-then-act: availability must be computed and hold inserted atomically
- Lock held only for one INSERT — very short-lived, low contention window

### DB-Level Safety Net (Partial Unique Indexes)

- [ ] One active hold per user per event: `WHERE status = 'ACTIVE'`
- [ ] One confirmed booking per user per event: `WHERE deleted = false`
- [ ] One booking per hold: `UNIQUE(hold_id)` prevents double-confirm race

### Concurrency Flow (Two Simultaneous Hold Requests)

```
User A                          User B (same event, same time)
│                               │
SELECT FOR UPDATE               │ (queued — waiting for lock)
│                               │
Check: 5 available seats        │
INSERT seat_holds (5 seats)     │
COMMIT → lock released          │
                                SELECT FOR UPDATE (acquired)
                                Check: 0 available seats
                                409 Conflict
```

---

## 7. Phase 7 — Hold Expiry Scheduler

### HoldExpiryScheduler

- [ ] `@Scheduled(fixedDelay = 60_000)` (every 60s)
- [ ] Single batch UPDATE:
  ```sql
  UPDATE seat_holds
  SET status = 'EXPIRED'
  WHERE status = 'ACTIVE' AND expires_at < now()
  ```
- [ ] Log: `"{N} holds expired"`
- [ ] Record `hold.expiry.batch.size` histogram metric

### ShedLock Config (Distributed Lock for Multi-Pod Deploy)

```java
@SchedulerLock(name = "HoldExpiryScheduler", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")
```

- [ ] ShedLock table (`V005__create_shedlock.sql`) must be present
- [ ] Only one pod runs the scheduler at a time — safe in horizontally scaled deploy

### Why Batch UPDATE vs Per-Hold Timers

- Per-hold timers create one JVM timer per active hold — heap + thread pressure at scale
- Single batch UPDATE is one DB round-trip per minute regardless of active hold count
- On pod restart: scheduler runs immediately, cleans all `expires_at < now()` — no stale state

---

## 8. Phase 8 — Kafka (Analytics Events)

> Unlike Library and Order services, Kafka here is analytics-only. No critical business logic depends on Kafka consumption.

### Topics & Purpose

| Topic | Partitions | Producer | Consumer | Purpose |
|---|---|---|---|---|
| `seat.hold.created` | 3 | `HoldService` | Downstream analytics | Track hold creation rate |
| `booking.confirmed` | 3 | `BookingService` | Downstream analytics | Track booking confirmation |

### Producer Config

```yaml
spring.kafka.producer:
  acks: all
  retries: 3
  properties:
    enable.idempotence: true
```

### Publish Points

- [ ] After successful hold INSERT: `kafkaTemplate.send("seat.hold.created", holdId, holdPayload)`
- [ ] After successful booking INSERT: `kafkaTemplate.send("booking.confirmed", bookingId, bookingPayload)`
- [ ] Both are fire-and-forget (analytics) — failure logged but does not affect API response

### Payload Schema

```json
// seat.hold.created
{ "holdId": "...", "eventId": "...", "userId": "...", "seatCount": 2, "expiresAt": "..." }

// booking.confirmed
{ "bookingId": "...", "holdId": "...", "eventId": "...", "userId": "...", "seatCount": 2, "confirmedAt": "..." }
```

---

## 9. Phase 9 — Security

### Spring Security Config

- [ ] Add `JwtAuthFilter` from `shared-lib` to security filter chain
- [ ] Permit `/actuator/**` unauthenticated
- [ ] All `/api/v1/**` require valid JWT

### Role Guards

| Endpoint | USER | ADMIN |
|---|---|---|
| `POST /events` | ❌ | ✅ |
| `GET /events/{id}` | ✅ | ✅ |
| `GET /events/{id}/availability` | ✅ | ✅ |
| `POST /holds` | ✅ | ✅ |
| `POST /bookings/confirm` | ✅ (own hold) | ✅ (any) |
| `GET /bookings/{id}` | ✅ (own) | ✅ (any) |
| `DELETE /bookings/{id}` | ✅ (own) | ✅ (any) |

### Security Invariants

- [ ] `userId` always derived from JWT `sub` — never from request body
- [ ] `POST /bookings/confirm` validates hold ownership before processing
- [ ] `DELETE /bookings/{id}` validates booking ownership before soft delete
- [ ] `totalSeats` immutable after event creation — service rejects any update attempt
- [ ] Rate limit: max 5 `POST /holds` per user per minute (via token bucket or Bucket4j)

---

## 10. Phase 10 — Observability

### Micrometer Metrics to Register

- [ ] `Counter`: `holds.created.total`
- [ ] `Counter`: `holds.expired.total`
- [ ] `Gauge`: `holds.active.gauge` — `COUNT(*) WHERE status=ACTIVE AND expires_at > now()`
- [ ] `Counter`: `bookings.confirmed.total`
- [ ] `Histogram`: `hold.expiry.batch.size` — spike indicates low confirm rate
- [ ] `Timer`: `availability.query.duration` — p95 alert > 80ms
- [ ] `Counter`: `overbooking.rejected.total` — spike → investigate event capacity

### Actuator & Logging

- [ ] `management.endpoints.web.exposure.include: health, prometheus`
- [ ] Liveness: DB + Kafka connectivity
- [ ] MDC fields: `traceId`, `spanId`, `userId`, `requestId`
- [ ] Log `holdId`, `eventId` but never PII (user email, name) in application logs

---

## 11. Phase 11 — Testing

### Unit Tests (Mockito)

- [ ] `HoldServiceTest`:
  - Create hold: happy path → status ACTIVE
  - Insufficient seats → 409
  - seatCount > MAX_SEATS_PER_HOLD → 400
  - Event in past → 400
  - Duplicate active hold (same user+event) → 409
- [ ] `BookingServiceTest`:
  - Confirm hold: happy path → CONFIRMED booking + hold CONFIRMED
  - Expired hold → 410
  - Wrong user → 403
  - Already confirmed hold → 409
  - Cancel booking: happy path, past event → 409
- [ ] `EventServiceTest`: create (future date), availability computation
- [ ] `HoldExpirySchedulerTest`: verifies batch UPDATE called, metrics incremented

### Integration Tests (`@SpringBootTest` + Testcontainers)

- [ ] `HoldControllerIT` — all scenarios including concurrent request pair
- [ ] `BookingControllerIT` — confirm flow, expiry flow, cancellation
- [ ] `EventControllerIT` — create, availability endpoint

### Concurrency Tests (Thread Pool)

- [ ] Two users request last N seats simultaneously → only one succeeds, other gets 409
- [ ] Two threads call `POST /bookings/confirm` with same holdId simultaneously → one 201, one 409 (unique constraint)
- [ ] Verify `overbooking.rejected.total` counter increments on rejection

### Scheduler Tests

- [ ] `HoldExpirySchedulerTest`: insert ACTIVE hold with `expires_at` in past → run scheduler → hold status becomes EXPIRED
- [ ] ShedLock: two scheduler invocations fire simultaneously → only one executes (requires integration test with real DB)

### Coverage Targets

| Layer | Target |
|---|---|
| Service | 80%+ line coverage |
| Controllers | All endpoints + all error paths |
| Concurrency | Overbooking scenario (last-seat race) |
| Scheduler | Expiry batch + ShedLock |

---

## 12. Checklist Summary

- [ ] `ticket-service` module scaffold + application.yml
- [ ] ShedLock dependency added to POM
- [ ] Liquibase migrations (users, events, seat_holds, bookings, shedlock)
- [ ] Partial UNIQUE indexes (active hold, no double booking)
- [ ] JPA entities: Event, User, SeatHold, Booking
- [ ] `EventRepository.findByIdWithLock()` with `@Lock(PESSIMISTIC_WRITE)`
- [ ] Availability query (native SQL)
- [ ] `EventService` + `EventController`
- [ ] `HoldService` (SELECT FOR UPDATE hold creation)
- [ ] `HoldController`
- [ ] DTOs + MapStruct mappers
- [ ] JWT filter + security config + role guards
- [ ] Custom 410 Gone error response
- [ ] `BookingService` (confirm: atomic INSERT+UPDATE, expire check, ownership)
- [ ] `BookingController` (confirm, get, cancel)
- [ ] `HoldExpiryScheduler` (60s batch UPDATE)
- [ ] ShedLock config on scheduler
- [ ] Kafka producer for `seat.hold.created` and `booking.confirmed`
- [ ] Rate limiting on `POST /holds` (max 5/min per user)
- [ ] Micrometer metrics (all 7 metrics)
- [ ] Actuator health + Prometheus
- [ ] Structured logging (MDC)
- [ ] Unit + Integration + Concurrency + Scheduler tests
- [ ] Test coverage ≥ 80% on service layer
- [ ] Overbooking concurrency test (thread-pool test)
