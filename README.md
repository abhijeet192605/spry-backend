# Spry Therapeutics — Backend Coding Exercise

Three independent Spring Boot microservices demonstrating REST API design, asynchronous processing with Apache Kafka, PostgreSQL schema design, and concurrency handling.

---

## Services at a Glance

| Service | Port | Requirement |
|---|---|---|
| `library-service` | `8081` | Book inventory with async wishlist notifications |
| `order-service` | `8082` | E-commerce orders with async background finalization |
| `ticket-service` | `8083` | Event ticket booking with hold-then-confirm workflow |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA / Hibernate 6 |
| Migrations | Liquibase |
| Messaging | Apache Kafka |
| Build | Maven (multi-module) |

---

## How to Run

### Prerequisites
- Java 17+, Maven 3.8+, Docker + Docker Compose

### 1. Start infrastructure (PostgreSQL + Kafka)

```bash
cd spry-backend
docker-compose up -d
```

This starts:
- PostgreSQL on `5432` with three databases: `library_db`, `order_db`, `ticket_db`
- Kafka + Zookeeper on `9092`

### 2. Start each service

```bash
# Terminal 1
cd library-service && mvn spring-boot:run

# Terminal 2
cd order-service && mvn spring-boot:run

# Terminal 3
cd ticket-service && mvn spring-boot:run
```

Liquibase runs automatically on startup and creates all tables.

### 3. Run tests

```bash
# From spry-backend root
mvn test
```

---

## Service 1 — Library Service (`:8081`)

### What it does
Manages a book inventory. When a book's status changes from `BORROWED` → `AVAILABLE`, it asynchronously notifies all users who have wishlisted that book, without blocking the API response.

### API Endpoints

| Method | Endpoint | Status | Description |
|---|---|---|---|
| `POST` | `/api/v1/books` | `201` | Create a book |
| `GET` | `/api/v1/books` | `200` | Paginated list with optional filters (`author`, `year`, `status`) |
| `GET` | `/api/v1/books/search?q=` | `200` | Partial-match search by title or author (min 2 chars) |
| `GET` | `/api/v1/books/{id}` | `200` | Get book by ID |
| `PUT` | `/api/v1/books/{id}` | `200` | Full update |
| `PATCH` | `/api/v1/books/{id}/status` | `200` | Update availability status |
| `DELETE` | `/api/v1/books/{id}` | `204` | Soft delete |
| `POST` | `/api/v1/wishlist` | `201` | Add book to wishlist |
| `DELETE` | `/api/v1/wishlist/{bookId}?userId=` | `204` | Remove from wishlist (idempotent) |

### Sample Requests

**Create a book**
```json
POST /api/v1/books
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "9780132350884",
  "publishedYear": 2008,
  "availabilityStatus": "AVAILABLE"
}
```

**Update status (triggers async notification)**
```json
PATCH /api/v1/books/{id}/status
{
  "status": "AVAILABLE"
}
```

**Add to wishlist**
```json
POST /api/v1/wishlist
{
  "bookId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "a3bb189e-8bf9-3888-9912-ace4e6543002"
}
```

### Key Design Decisions

**Async Notification via Outbox Pattern**

When `PATCH /books/{id}/status` is called:
1. Book update + outbox event inserted in **one transaction** → no event loss
2. `OutboxRelayService` polls every 500 ms and publishes to Kafka topic `book.status.changed`
3. `NotificationConsumer` reads the event, queries the wishlist, and logs one notification per user
4. The API returns immediately — notification happens in the background

```
PATCH /status  →  [DB: update book + insert outbox_event]  →  202 returned
                                    ↓ (async, ~500ms)
                          OutboxRelayService → Kafka
                                    ↓
                          NotificationConsumer
                          → logs: "Notification prepared for {userId}: Book [Clean Code] is now available."
```

**Validation**
- Duplicate ISBN rejected (`409 Conflict`)
- `publishedYear` must be between 1000 and current year
- Search query requires minimum 2 characters

**Soft Delete**
Every delete sets `deleted = true`, `deleted_at`, `deleted_by`. All queries filter `WHERE deleted = false`.

---

## Service 2 — Order Service (`:8082`)

### What it does
Manages customers and their orders. Order creation is non-blocking — it returns `202 Accepted` immediately with `PENDING` status, then a background Kafka consumer calculates the total and sets the status to `CONFIRMED`.

### API Endpoints

| Method | Endpoint | Status | Description |
|---|---|---|---|
| `POST` | `/api/v1/customers` | `201` | Create a customer |
| `GET` | `/api/v1/customers/{id}` | `200` | Get customer |
| `POST` | `/api/v1/orders` | `202` | Create order (immediate response, async finalization) |
| `GET` | `/api/v1/orders/{id}` | `200` | Get order — includes `totalValue` once confirmed |
| `GET` | `/api/v1/customers/{id}/orders` | `200` | Paginated order list, filterable by status |
| `PATCH` | `/api/v1/orders/{id}/status` | `200` | Manually update status (requires `version` for optimistic lock) |
| `DELETE` | `/api/v1/orders/{id}` | `204` | Soft delete |

### Sample Requests

**Create a customer**
```json
POST /api/v1/customers
{
  "name": "Alice Smith",
  "email": "alice@example.com",
  "address": "123 Main St"
}
```

**Create an order** ← returns 202 immediately, totalValue is null
```json
POST /api/v1/orders
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "orderDate": "2026-04-19T10:00:00Z",
  "items": [
    { "productName": "Clean Code", "unitPrice": 39.99, "quantity": 2 }
  ]
}
```

**Response (202 Accepted)**
```json
{
  "id": "a3bb189e-...",
  "status": "PENDING",
  "totalValue": null,
  "items": [...]
}
```

**After background finalization, GET /orders/{id} returns:**
```json
{
  "id": "a3bb189e-...",
  "status": "CONFIRMED",
  "totalValue": 79.98,
  "items": [...]
}
```

**Update status with optimistic lock**
```json
PATCH /api/v1/orders/{id}/status
{
  "status": "SHIPPED",
  "version": 1
}
```

### Key Design Decisions

**202 Accepted + Async Finalization**

```
POST /orders  →  [DB: insert order(PENDING) + insert outbox_event]  →  202 returned
                                    ↓ (async)
                          OutboxRelayService → Kafka: order.created
                                    ↓
                          OrderFinalizationConsumer
                          → calculates SUM(unitPrice × quantity)
                          → updates: status = CONFIRMED, totalValue = 79.98
```

**Optimistic Concurrency Control**
- `Order` entity has a `@Version` field (starts at `0`)
- `PATCH /status` requires the client to pass the current `version`
- If two clients try to update simultaneously, the second gets `409 Conflict`
- `ObjectOptimisticLockingFailureException` is caught and translated to a clean error response

**Stuck Order Recovery**
A scheduler runs every 10 minutes. Any order stuck in `PENDING` for more than 5 minutes gets re-queued to Kafka automatically — handles cases where the consumer failed mid-flight.

**Valid Status Transitions**

```
PENDING  →  CANCELLED
CONFIRMED  →  SHIPPED | CANCELLED
SHIPPED  →  (terminal, no transitions)
```

**Soft Delete**
Orders cannot be deleted if status is `SHIPPED`. All other statuses allow soft delete.

---

## Service 3 — Ticket Service (`:8083`)

### What it does
Manages event ticket booking using a hold-then-confirm workflow. A user first places a 5-minute hold on seats; a separate confirm step converts it to a permanent booking. Concurrent requests are handled safely — the system guarantees seats are never overbooked.

### API Endpoints

| Method | Endpoint | Status | Description |
|---|---|---|---|
| `POST` | `/api/v1/events` | `201` | Create an event |
| `GET` | `/api/v1/events/{id}` | `200` | Get event details |
| `GET` | `/api/v1/events/{id}/availability` | `200` | Real-time available seats |
| `POST` | `/api/v1/holds` | `201` | Place a 5-minute seat hold — returns `holdId` |
| `POST` | `/api/v1/bookings/confirm` | `201` | Convert hold to permanent booking |
| `GET` | `/api/v1/bookings/{id}` | `200` | Get booking details |
| `DELETE` | `/api/v1/bookings/{id}` | `204` | Cancel booking (soft delete) |

### Sample Requests

**Create an event**
```json
POST /api/v1/events
{
  "name": "Tech Conference 2026",
  "eventDate": "2026-09-15T09:00:00Z",
  "location": "San Francisco, CA",
  "totalSeats": 500
}
```

**Place a hold** ← seats reserved for 5 minutes
```json
POST /api/v1/holds
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "a3bb189e-8bf9-3888-9912-ace4e6543002",
  "seatCount": 2
}
```

**Response (201 Created)**
```json
{
  "holdId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventId": "550e8400-...",
  "seatCount": 2,
  "status": "ACTIVE",
  "expiresAt": "2026-04-19T10:05:00Z"
}
```

**Confirm booking** ← must happen before `expiresAt`
```json
POST /api/v1/bookings/confirm
{
  "holdId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "userId": "a3bb189e-8bf9-3888-9912-ace4e6543002"
}
```

**Check availability**
```json
GET /api/v1/events/{id}/availability

{
  "eventId": "550e8400-...",
  "totalSeats": 500,
  "confirmedSeats": 120,
  "activeHoldSeats": 30,
  "availableSeats": 350,
  "computedAt": "2026-04-19T10:01:00Z"
}
```

### Key Design Decisions

**Hold-Then-Confirm Workflow**

```
POST /holds  →  SELECT FOR UPDATE on event row  →  check availability
                     → insert seat_hold (status=ACTIVE, expiresAt=now+5min)
                     → 201 { holdId }

POST /bookings/confirm  →  validate hold is ACTIVE + not expired
                        →  insert booking (status=CONFIRMED)
                        →  update hold (status=CONFIRMED)
                        →  201 { bookingId }
```

**Overbooking Prevention (Pessimistic Locking)**

`POST /holds` acquires a `SELECT FOR UPDATE` lock on the event row. Concurrent hold requests are serialized at the database level:

```
available = totalSeats - confirmedBookings - activeHolds
if available < requested → 409 Conflict "Not enough seats"
```

**Double-Booking Prevention**

A partial unique index in PostgreSQL ensures one active booking per user per event:
```sql
CREATE UNIQUE INDEX uq_booking_user_event
  ON bookings (user_id, event_id)
  WHERE deleted = false;
```

**Automatic Hold Expiry**

A scheduler runs every 60 seconds:
```sql
UPDATE seat_holds
SET status = 'EXPIRED'
WHERE status = 'ACTIVE' AND expires_at < now()
```

ShedLock ensures only one pod runs the scheduler at a time in a multi-instance deployment.

**Error Responses**

| Scenario | Status |
|---|---|
| Hold already expired at confirm time | `410 Gone` |
| Not enough seats | `409 Conflict` |
| Event/hold/booking not found | `404 Not Found` |
| Event date in the past | `400 Bad Request` |

**Soft Delete for Cancellations**
Cancelled bookings set `status = CANCELLED` and `deleted = true`. The availability query excludes them automatically.

---

## Cross-Cutting Concerns

### Shared Library (`shared-lib`)
All three services depend on a common module providing:
- `SoftDeletableEntity` — base class with `deleted`, `deleted_at`, `deleted_by` fields
- `GlobalExceptionHandler` — RFC 7807 `application/problem+json` error responses
- `ApiError` — consistent error envelope with field-level validation messages

### Error Response Format
```json
{
  "status": 400,
  "title": "Validation Failed",
  "errors": [
    "isbn: must not be blank",
    "publishedYear: must be a valid year"
  ]
}
```

### Outbox Pattern (library + order services)
Guarantees no event loss even if Kafka is temporarily unavailable:
1. Domain change + outbox event inserted in **one DB transaction**
2. Relay service polls `outbox_events` table and publishes to Kafka
3. Row marked `published = true` after successful publish
4. If Kafka is down, events queue up in the DB and flush when it recovers

### Idempotent Consumers
All Kafka consumers guard against duplicate processing:
- `NotificationConsumer` — unique constraint on `kafka_event_id` prevents double notification logging
- `OrderFinalizationConsumer` — checks `status == PENDING` before processing; skips already-confirmed orders
