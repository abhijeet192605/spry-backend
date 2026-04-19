# Ticket Booking Service — Architecture

> **Service:** `ticket-service`  
> **Port:** `8083`  
> **Database:** `ticket_db` (PostgreSQL 15)  
> **Kafka Topics:** `seat.hold.created`, `booking.confirmed`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [Technology Stack](#3-technology-stack)
4. [Domain Model](#4-domain-model)
5. [Database Schema](#5-database-schema)
6. [API Reference](#6-api-reference)
7. [Hold → Confirm → Expiry Flow](#7-hold--confirm--expiry-flow)
8. [Concurrency & Overbooking Prevention](#8-concurrency--overbooking-prevention)
9. [Kafka Design](#9-kafka-design)
10. [Security](#10-security)
11. [Observability](#11-observability)
12. [Failure Modes & Mitigations](#12-failure-modes--mitigations)
13. [Non-Functional Requirements](#13-non-functional-requirements)
14. [Key Design Decisions](#14-key-design-decisions)

---

## 1. Overview

The Ticket Booking Service handles event creation and seat booking with robust concurrency control. It implements a **hold-then-confirm** workflow — seats are temporarily reserved for 5 minutes before a booking is finalised. A scheduled background job automatically releases expired holds, making seats available again without any manual intervention.

### Core Responsibilities

| Responsibility | Mechanism |
|---|---|
| Event management (CRUD) | REST API + PostgreSQL |
| Real-time seat availability | Computed query — total minus confirmed and active holds |
| Temporary seat hold (5 min TTL) | `SEAT_HOLDS` table + `expires_at` field |
| Hold confirmation → Booking | Atomic transaction (INSERT + UPDATE) |
| Auto-release of expired holds | `@Scheduled` batch UPDATE every 60s |
| Overbooking prevention | `SELECT FOR UPDATE` + DB constraints |
| Double-booking prevention | Partial UNIQUE indexes |
| Soft cancel with audit trail | `deleted` + `deleted_at` + `deleted_by` |

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ticket-service                               │
│                                                                     │
│  REST Client                                                        │
│      │                                                              │
│      ▼                                                              │
│  ┌──────────────────────────────────────────────┐                   │
│  │  EventController                             │                   │
│  │  HoldController    ──▶  HoldService          │                   │
│  │  BookingController ──▶  BookingService       │                   │
│  └──────────────────────────────┬───────────────┘                   │
│                                 │ @Transactional                   │
│                                 │ SELECT FOR UPDATE (holds)        │
│                                 │ Atomic INSERT+UPDATE (confirm)   │
│                                 ▼                                   │
│                        ┌─────────────────┐                          │
│                        │   ticket_db      │  PostgreSQL 15           │
│                        │  (PostgreSQL)    │                          │
│                        └─────────────────┘                          │
│                                 ▲                                   │
│                        ┌────────┴────────┐                          │
│                        │HoldExpiryScheduler @Scheduled(60s)         │
│                        │UPDATE status=EXPIRED                       │
│                        │WHERE status=ACTIVE AND expires_at < now()  │
│                        └─────────────────┘                          │
│                                                                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ publish events
                                ▼
               ┌────────────────────────────────┐
               │  seat.hold.created             │  Kafka Topics
               │  booking.confirmed             │  (analytics / downstream)
               └────────────────────────────────┘
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
| Scheduler Lock | ShedLock | 5.x |
| Validation | Jakarta Bean Validation | 3.x |
| Observability | Micrometer + OpenTelemetry | — |
| Mapping | MapStruct | 1.5.x |
| Build | Maven | 3.x |

---

## 4. Domain Model

```
┌───────────────┐          ┌───────────────────────────┐
│    EVENTS     │          │        SEAT_HOLDS          │
│───────────────│          │───────────────────────────│
│ id (PK)       │1────────N│ id (PK)  ← holdId         │
│ name          │          │ event_id (FK)              │
│ event_date    │          │ user_id (FK)               │
│ location      │          │ seat_count                 │
│ total_seats   │          │ status   ← ACTIVE/CONFIRMED│
│ created_at    │          │           /EXPIRED         │
│ updated_at    │          │ expires_at ← +5 min        │
└───────────────┘          │ created_at                 │
                           └──────────────┬─────────────┘
                                          │ 1
                                          │
                                          │ 0..1
                           ┌──────────────▼─────────────┐
                           │         BOOKINGS            │
                           │────────────────────────────│
                           │ id (PK)                    │
                           │ hold_id (FK, UQ) ← 1:1     │
                           │ event_id (FK)              │
                           │ user_id (FK)               │
                           │ seat_count ← snapshot      │
                           │ status ← CONFIRMED/CANCELLED│
                           │ deleted                    │
                           │ deleted_at                 │
                           │ deleted_by                 │
                           │ created_at                 │
                           └────────────────────────────┘

┌───────────────┐
│    USERS      │
│───────────────│
│ id (PK)       │
│ name          │
│ email (UQ)    │
│ created_at    │
└───────────────┘
```

### Enums

| Enum | Values |
|---|---|
| `HoldStatus` | `ACTIVE`, `CONFIRMED`, `EXPIRED` |
| `BookingStatus` | `CONFIRMED`, `CANCELLED` |

### Hold Lifecycle

```
                    POST /holds
                        │
                        ▼
                    ACTIVE ──── expires in 5 min ────▶ EXPIRED
                        │
                        │  POST /bookings/confirm (within 5 min)
                        │
                        ▼
                   CONFIRMED ──── creates ──────────▶ BOOKING (CONFIRMED)
```

---

## 5. Database Schema

### `events`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | `gen_random_uuid()` |
| `name` | `VARCHAR(300)` | NOT NULL | — |
| `event_date` | `TIMESTAMPTZ` | NOT NULL | CHECK: must be future date on creation |
| `location` | `VARCHAR(400)` | NOT NULL | — |
| `total_seats` | `INTEGER` | NOT NULL | CHECK (> 0) · immutable after creation |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | — |

### `users`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `name` | `VARCHAR(200)` | NOT NULL | — |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | — |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |

### `seat_holds`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | Returned as `holdId` to client |
| `event_id` | `UUID` | FK → events.id, IDX | NOT NULL |
| `user_id` | `UUID` | FK → users.id, IDX | NOT NULL |
| `seat_count` | `INTEGER` | NOT NULL | CHECK (1–10, configurable max) |
| `status` | `VARCHAR(20)` | NOT NULL, IDX | DEFAULT `ACTIVE` |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL, IDX | `created_at + INTERVAL '5 min'` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | DEFAULT `now()` |

### `bookings`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `hold_id` | `UUID` | FK → seat_holds.id, UNIQUE | NOT NULL · 1:1 with hold |
| `event_id` | `UUID` | FK → events.id, IDX | NOT NULL |
| `user_id` | `UUID` | FK → users.id, IDX | NOT NULL |
| `seat_count` | `INTEGER` | NOT NULL | Snapshot from hold at confirm time |
| `status` | `VARCHAR(20)` | NOT NULL | CONFIRMED / CANCELLED |
| `deleted` | `BOOLEAN` | NOT NULL | DEFAULT `false` |
| `deleted_at` | `TIMESTAMPTZ` | nullable | Set on soft cancel |
| `deleted_by` | `UUID` | nullable | Actor who cancelled |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |

### Indexes & Constraints Summary

```sql
-- Events
CHECK (total_seats > 0);
-- total_seats is immutable — enforced at application layer

-- Seat Holds
CREATE INDEX ON seat_holds (event_id, status, expires_at);  -- composite: availability + expiry

-- Partial UNIQUE index — one active hold per user per event
CREATE UNIQUE INDEX idx_seat_holds_one_active_per_user_event
ON seat_holds (user_id, event_id)
WHERE status = 'ACTIVE';

-- Bookings
CREATE UNIQUE INDEX ON bookings (hold_id);   -- one booking per hold (prevents double-confirm)

-- Partial UNIQUE index — one confirmed booking per user per event
CREATE UNIQUE INDEX idx_bookings_no_double_booking
ON bookings (user_id, event_id)
WHERE deleted = false;
```

### Availability Query

```sql
SELECT
  e.total_seats
  - COALESCE((
      SELECT SUM(b.seat_count)
      FROM bookings b
      WHERE b.event_id = e.id
        AND b.status = 'CONFIRMED'
        AND b.deleted = false
    ), 0)
  - COALESCE((
      SELECT SUM(h.seat_count)
      FROM seat_holds h
      WHERE h.event_id = e.id
        AND h.status = 'ACTIVE'
        AND h.expires_at > now()
    ), 0)
  AS available_seats
FROM events e
WHERE e.id = :eventId;
```

---

## 6. API Reference

**Base URL:** `/api/v1`  
**Content-Type:** `application/json`  
**Error Format:** RFC 7807 `application/problem+json`

---

### `POST /events`
Create a new event.

**Request:**
```json
{
  "name": "Spring Boot Conference 2024",
  "eventDate": "2024-06-15T09:00:00Z",
  "location": "Pune Tech Park, Hall A",
  "totalSeats": 500
}
```

**Validation:**
- `name` — required, max 300 chars
- `eventDate` — required, must be a future date
- `location` — required, max 400 chars
- `totalSeats` — required, > 0, **immutable after creation**

**Response `201 Created`:**
```json
{
  "id": "evt-uuid-...",
  "name": "Spring Boot Conference 2024",
  "eventDate": "2024-06-15T09:00:00Z",
  "location": "Pune Tech Park, Hall A",
  "totalSeats": 500,
  "createdAt": "2024-01-15T08:00:00Z"
}
```

**Errors:** `400` Validation failed · `400` eventDate is in the past

---

### `GET /events/{id}`
Get event details.

**Response `200 OK`:** Event object.

---

### `GET /events/{id}/availability`
Real-time seat availability — computed at query time.

**Response `200 OK`:**
```json
{
  "eventId": "evt-uuid-...",
  "totalSeats": 500,
  "confirmedSeats": 320,
  "activeHoldSeats": 15,
  "availableSeats": 165,
  "computedAt": "2024-01-15T10:30:00Z"
}
```

> `availableSeats = totalSeats - confirmedSeats - activeHoldSeats(not expired)`

**Errors:** `404` Event not found

---

### `POST /holds`
Place a temporary seat hold. Hold expires in 5 minutes if not confirmed.

**Request:**
```json
{
  "eventId": "evt-uuid-...",
  "userId": "usr-uuid-...",
  "seatCount": 2
}
```

**Validation:**
- `eventId` — required, must exist, event must not have passed
- `userId` — required, the user placing the hold
- `seatCount` — required, 1–10 (configurable max per hold)
- Only 1 active hold per user per event is allowed (enforced at DB level)

**Response `201 Created`:**
```json
{
  "holdId": "hold-uuid-...",
  "eventId": "evt-uuid-...",
  "userId": "usr-uuid-...",
  "seatCount": 2,
  "status": "ACTIVE",
  "expiresAt": "2024-01-15T10:35:00Z",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

> The client **must** call `POST /bookings/confirm` with this `holdId` before `expiresAt`.

**Errors:** `400` Insufficient seats · `404` Event not found · `409` Active hold already exists for this user and event

---

### `POST /bookings/confirm`
Confirm a hold into a permanent booking.

**Request:**
```json
{
  "holdId": "hold-uuid-...",
  "userId": "usr-uuid-..."
}
```

**Business rules:**
- `userId` in request must match the hold's `userId`
- Hold must be in `ACTIVE` status
- Hold must not have expired (`expires_at > now()`)
- Operation is atomic: `INSERT bookings` + `UPDATE seat_holds SET status = CONFIRMED` in one transaction

**Response `201 Created`:**
```json
{
  "bookingId": "bkng-uuid-...",
  "holdId": "hold-uuid-...",
  "eventId": "evt-uuid-...",
  "userId": "usr-uuid-...",
  "seatCount": 2,
  "status": "CONFIRMED",
  "createdAt": "2024-01-15T10:32:00Z"
}
```

**Errors:**

| Code | Reason |
|---|---|
| `404` | Hold not found |
| `409` | Hold already confirmed or cancelled |
| `410 Gone` | Hold has expired — must create a new hold |

> **Why 410 and not 404?** `410 Gone` explicitly tells the client "this resource existed but is no longer valid." A `404` would imply it never existed. `410` gives the client the correct signal to restart the hold flow rather than retry the same confirm.

---

### `GET /bookings/{id}`
Get booking details.

**Response `200 OK`:**
```json
{
  "bookingId": "bkng-uuid-...",
  "eventId": "evt-uuid-...",
  "eventName": "Spring Boot Conference 2024",
  "eventDate": "2024-06-15T09:00:00Z",
  "userId": "usr-uuid-...",
  "seatCount": 2,
  "status": "CONFIRMED",
  "createdAt": "2024-01-15T10:32:00Z"
}
```

**Errors:** `404` Not found

---

### `DELETE /bookings/{id}`
Cancel a confirmed booking (soft delete).

**Response `204 No Content`**

> Soft cancel: sets `status = CANCELLED`, `deleted = true`, `deleted_at`. Seats are immediately made available for new holds after cancellation.

**Errors:** `404` Not found · `409` Event has already passed — cannot cancel

---

### Standard Error Response (RFC 7807)

```json
{
  "type": "https://api.spry.io/errors/resource-expired",
  "title": "Resource Expired",
  "status": 410,
  "detail": "Hold expired at 2024-01-15T10:35:00Z. Please create a new hold.",
  "instance": "/api/v1/bookings/confirm",
  "traceId": "abc123def456"
}
```

---

## 7. Hold → Confirm → Expiry Flow

### Full Workflow

```
User A                  TicketService            PostgreSQL         Kafka
  │                          │                       │               │
  │── POST /holds ──────────▶│                       │               │
  │                          │── BEGIN TX ──────────▶│               │
  │                          │── SELECT events        │               │
  │                          │   WHERE id=?           │               │
  │                          │   FOR UPDATE ─────────▶│               │
  │                          │◀─ event row locked ────│               │
  │                          │── compute available    │               │
  │                          │   seats                │               │
  │                          │── INSERT seat_holds ──▶│               │
  │                          │── COMMIT ─────────────▶│               │
  │                          │── send seat.hold.created────────────────▶│
  │◀── 201 { holdId,         │                       │               │
  │         expiresAt } ─────│                       │               │
  │                          │                       │               │
  │  (within 5 minutes)      │                       │               │
  │                          │                       │               │
  │── POST /bookings/confirm ▶│                       │               │
  │                          │── SELECT seat_holds   │               │
  │                          │   WHERE id=holdId      │               │
  │                          │   AND status=ACTIVE    │               │
  │                          │   AND expires_at>now()▶│               │
  │                          │── BEGIN TX            │               │
  │                          │── INSERT bookings ────▶│               │
  │                          │── UPDATE seat_holds    │               │
  │                          │   status=CONFIRMED ───▶│               │
  │                          │── COMMIT ─────────────▶│               │
  │                          │── send booking.confirmed────────────────▶│
  │◀── 201 { bookingId } ────│                       │               │
  │                          │                       │               │

  (if not confirmed within 5 min)

HoldExpiryScheduler       TicketService            PostgreSQL
       │                       │                       │
       │── @Scheduled(60s) ───▶│                       │
       │                       │── UPDATE seat_holds ──▶│
       │                       │   SET status=EXPIRED  │
       │                       │   WHERE status=ACTIVE │
       │                       │   AND expires_at<now()│
       │                       │◀── N rows updated ────│
       │                       │── log: N holds expired│
```

---

## 8. Concurrency & Overbooking Prevention

### The Problem
Without concurrency control, two users requesting the last 5 seats simultaneously could both succeed — resulting in 10 seats booked for an event with only 5 remaining.

### Solution: `SELECT FOR UPDATE`

When creating a hold, the service acquires a pessimistic write lock on the event row before checking availability:

```java
// In EventRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Event e WHERE e.id = :id")
Optional<Event> findByIdWithLock(@Param("id") UUID id);
```

This serialises concurrent hold requests for the same event:

```
User A requests hold          User B requests hold (same event, same time)
        │                                   │
        ▼                                   │
SELECT events WHERE id=?                    │  (queued — waiting for lock)
FOR UPDATE                                  │
        │                                   │
        ▼                                   │
Check availability: 5 seats                 │
INSERT seat_holds (5 seats)                 │
COMMIT → lock released                      │
                                            ▼
                               SELECT events WHERE id=?
                               FOR UPDATE (lock acquired)
                                            │
                               Check availability: 0 seats
                                            │
                               409 Conflict — no seats available
```

### Database-Level Safety Net

Even with application-level locking, a partial UNIQUE index provides a DB-level guarantee against a user holding more than one active hold per event:

```sql
CREATE UNIQUE INDEX idx_seat_holds_one_active_per_user_event
ON seat_holds (user_id, event_id)
WHERE status = 'ACTIVE';
```

And a second partial UNIQUE index prevents a user from having more than one confirmed booking per event:

```sql
CREATE UNIQUE INDEX idx_bookings_no_double_booking
ON bookings (user_id, event_id)
WHERE deleted = false;
```

These constraints enforce business rules **at the database level** — even if application logic has a bug, the DB rejects the invalid state.

### Why SELECT FOR UPDATE Here but Not in Order Service?

The ticket service uses **pessimistic** locking for holds because:
- **Conflict rate is high** — on popular events, many users compete for the same seats simultaneously
- **The check-then-act window matters** — availability must be computed and the hold inserted before any other thread changes it
- **Pessimistic lock is held only for the duration of one insert** — very short-lived

The order service uses **optimistic** locking because:
- **Conflict rate is low** — two admins updating the same order simultaneously is rare
- **No computed-then-act pattern** — status updates don't depend on reading another value

---

## 9. Kafka Design

### Topics

| Topic | Partitions | Purpose |
|---|---|---|
| `seat.hold.created` | 3 | Analytics — track hold creation rate |
| `booking.confirmed` | 3 | Analytics — downstream booking notifications |

> Unlike the Library and Order services, the Ticket service does not have a critical Kafka consumer driving core business logic. Kafka here is used for event streaming to downstream consumers (analytics, notification systems). The core hold and booking logic is synchronous and DB-driven.

### Producer Config

```yaml
kafka:
  producer:
    acks: all
    retries: 3
    enable.idempotence: true
```

---

## 10. Security

All endpoints are publicly accessible (no authentication required). Input validation is enforced via Jakarta Bean Validation on all request DTOs. `totalSeats` is immutable after event creation — enforced at service layer. No raw SQL — all queries via JPA / JPQL.

---

## 11. Observability

### Health Checks

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Liveness — DB + Kafka |
| `GET /actuator/health/readiness` | Readiness — DB pool |
| `GET /actuator/prometheus` | Prometheus metrics |

### Key Metrics

| Metric | Type | Alert Condition |
|---|---|---|
| `holds.created.total` | Counter | — |
| `holds.expired.total` | Counter | — |
| `holds.active.gauge` | Gauge | — (monitoring) |
| `bookings.confirmed.total` | Counter | — |
| `hold.expiry.batch.size` | Histogram | Spike → low confirm rate |
| `availability.query.duration` | Timer | p95 > 80ms → alert |
| `overbooking.rejected.total` | Counter | Spike → investigate capacity |

---

## 12. Failure Modes & Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Two users request last N seats simultaneously | Potential overbooking | `SELECT FOR UPDATE` serialises hold creation; second request sees updated availability |
| User confirms an expired hold | 410 returned | Explicit `expires_at > now()` check before processing; hold set to EXPIRED |
| User confirms same hold twice | Double booking | `UNIQUE(hold_id)` on bookings table — second INSERT fails with constraint violation → 409 |
| Two different users book for same event | Double booking | Partial UNIQUE index `(user_id, event_id) WHERE deleted = false` |
| HoldExpiryScheduler crashes (pod restart) | Expired holds not released | On next startup, scheduler runs and cleans up all holds with `expires_at < now()` — DB state remains consistent |
| Multiple pod instances run scheduler simultaneously | Duplicate expiry updates (harmless but wasteful) | ShedLock — distributed lock ensures only one pod runs the scheduler job at a time |
| Kafka unavailable | `seat.hold.created` not published | Hold is already persisted in DB — analytics event loss acceptable; retry on next publish attempt |

---

## 13. Non-Functional Requirements

| Requirement | Target |
|---|---|
| `POST /holds` p95 response time | < 300ms (includes SELECT FOR UPDATE contention) |
| `GET /events/{id}/availability` p95 | < 80ms |
| `POST /bookings/confirm` p95 | < 200ms |
| Hold TTL | 5 minutes (configurable via env var `HOLD_TTL_MINUTES`) |
| Expiry scheduler frequency | Every 60 seconds |
| Max seats per hold | 10 (configurable via env var `MAX_SEATS_PER_HOLD`) |
| Confirmed bookings retention | Indefinite (event history) |
| Cancelled bookings retention | 2 years (soft-deleted, then eligible for purge) |
| Service availability | 99.9% |

---

## 14. Key Design Decisions

### Why hold-then-confirm instead of direct booking?
Direct booking under concurrent load creates a race condition — two users both see 1 seat available and both try to book simultaneously, resulting in overbooking. The hold-then-confirm pattern uses `SELECT FOR UPDATE` to serialise seat reservation and a short TTL to prevent seat squatting. This is the same pattern used by ticketing platforms like BookMyShow and Ticketmaster.

### Why `SELECT FOR UPDATE` (pessimistic lock) and not `@Version` (optimistic lock)?
In the hold creation flow, availability is **computed from the DB** and the decision to allow or deny the hold depends on that computation. This is a classic check-then-act race condition that pessimistic locking solves definitively. Optimistic locking only detects conflicts after the fact — by the time a version mismatch is detected, the hold has already been inserted. Pessimistic locking prevents the inconsistency from occurring in the first place.

### Why 410 Gone for expired holds?
HTTP `410 Gone` is semantically precise: the resource existed but is no longer available and is not expected to be available again. This tells the client exactly what happened and what to do — create a new hold. `404 Not Found` would imply the hold never existed, which is incorrect and would confuse clients that stored the `holdId` from the original response.

### Why a scheduled batch job for expiry instead of per-hold timers?
Per-hold timers (e.g. `@Scheduled` with a fixed delay per hold, or Spring's `TaskScheduler`) would create one timer per active hold. Under load (thousands of concurrent holds), this is a JVM heap and thread pressure problem. A single scheduled job that runs every 60 seconds issues one `UPDATE` statement that expires all stale holds in a single DB round-trip — far more efficient and operationally simpler.

### Why UNIQUE(hold_id) on bookings?
This is the primary guard against the confirm-twice race condition. If two threads both pass the "is hold ACTIVE?" check simultaneously and both attempt to insert a booking, only one will succeed — the second fails with a unique constraint violation, which the service maps to `409 Conflict`. Without this constraint, a race condition between the check and the insert would allow duplicate bookings to be created.

### Why is totalSeats immutable after event creation?
Changing `total_seats` after an event has active holds and confirmed bookings would require recalculating availability and potentially invalidating existing holds. The complexity and risk of data inconsistency outweigh the benefit. If capacity needs to increase, a new field `additional_capacity` could be added as a separate concern. Immutability is enforced at the service layer — the `PUT /events/{id}` endpoint rejects requests that attempt to change `total_seats`.

### Why partial UNIQUE indexes instead of application-layer uniqueness checks?
Application-layer checks (e.g. `if (holdRepository.existsActiveHold(userId, eventId)) throw`) are subject to race conditions — two concurrent requests both pass the check before either has inserted. Partial UNIQUE indexes enforce the constraint at the DB level, which is atomic and immune to race conditions regardless of how many concurrent requests arrive simultaneously.
