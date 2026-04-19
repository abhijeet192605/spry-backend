# Order Management Service — Architecture

> **Service:** `order-service`  
> **Port:** `8082`  
> **Database:** `order_db` (PostgreSQL 15)  
> **Kafka Topics:** `order.created`, `order.created.DLQ`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [Technology Stack](#3-technology-stack)
4. [Domain Model](#4-domain-model)
5. [Database Schema](#5-database-schema)
6. [API Reference](#6-api-reference)
7. [Async Flow — Order Finalization](#7-async-flow--order-finalization)
8. [Kafka Design](#8-kafka-design)
9. [Concurrency & Optimistic Locking](#9-concurrency--optimistic-locking)
10. [Security](#10-security)
11. [Observability](#11-observability)
12. [Failure Modes & Mitigations](#12-failure-modes--mitigations)
13. [Non-Functional Requirements](#13-non-functional-requirements)
14. [Key Design Decisions](#14-key-design-decisions)

---

## 1. Overview

The Order Management Service handles customer order placement in an e-commerce system. It is designed for high responsiveness — order creation returns immediately with a `202 Accepted` response, while total calculation and order confirmation happen asynchronously via a Kafka consumer. A scheduled recovery job ensures no order is silently stuck in `PENDING` state indefinitely.

### Core Responsibilities

| Responsibility | Mechanism |
|---|---|
| Customer management (CRUD) | REST API + PostgreSQL |
| Order creation (fast path) | 202 Accepted + Outbox pattern |
| Async order finalization | Kafka consumer — calculates total, confirms order |
| Order status management | PATCH with optimistic locking |
| Stuck order recovery | Scheduled job — re-triggers finalization |
| Soft delete with audit trail | `deleted` + `deleted_at` + `deleted_by` |
| Concurrent update safety | `@Version` optimistic locking |

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         order-service                               │
│                                                                     │
│  REST Client                                                        │
│      │                                                              │
│      ▼                                                              │
│  ┌──────────────────┐     ┌────────────────────────────┐            │
│  │ OrderController   │────▶│       OrderService          │           │
│  │ CustomerController│     │ validate → INSERT PENDING   │           │
│  └──────────────────┘     │ + INSERT outbox_events      │           │
│                            │ (single @Transactional)    │           │
│                            └──────────────┬─────────────┘           │
│                                           │                         │
│                            ┌──────────────▼─────────────┐           │
│                            │        order_db             │           │
│                            │      (PostgreSQL 15)        │           │
│                            └──────────────┬─────────────┘           │
│                                           │                         │
│                            ┌──────────────▼─────────────┐           │
│                            │    OutboxRelayService       │           │
│                            │    @Scheduled (500ms)       │           │
│                            └──────────────┬─────────────┘           │
│                                           │                         │
└───────────────────────────────────────────┼─────────────────────────┘
                                            │ publish
                                            ▼
                                 ┌──────────────────┐
                                 │  order.created   │  Kafka Topic
                                 └────────┬─────────┘
                                          │
                    ┌─────────────────────┤
                    │                     │ @KafkaListener
                    │                     ▼
                    │          ┌──────────────────────────┐
                    │          │  OrderFinalizationConsumer│
                    │          │  SUM(line_total)          │
                    │          │  UPDATE status=CONFIRMED  │
                    │          │  @Version optimistic lock │
                    │          └──────────────────────────┘
                    │                     │ on failure × 3
                    │                     ▼
                    │          ┌──────────────────────┐
                    │          │   order.created.DLQ  │
                    │          └──────────────────────┘
                    │
                    │ @Scheduled(10min)
                    ▼
         ┌─────────────────────────────┐
         │  StuckOrderRecoveryScheduler │
         │  finds PENDING > 5 min      │
         │  re-publishes to Kafka       │
         └─────────────────────────────┘
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
┌──────────────┐           ┌─────────────────────────┐
│  CUSTOMERS   │           │         ORDERS           │
│──────────────│           │─────────────────────────│
│ id (PK)      │           │ id (PK)                 │
│ name         │           │ customer_id (FK)         │
│ email (UQ)   │1─────────N│ status                  │
│ address      │           │ total_value (nullable)   │
│ created_at   │           │ order_date               │
│ updated_at   │           │ version  ← @Version      │
└──────────────┘           │ deleted                 │
                           │ deleted_at               │
                           │ deleted_by               │
                           │ created_at               │
                           │ updated_at               │
                           └────────────┬─────────────┘
                                        │ 1
                                        │
                                        │ N
                           ┌────────────▼─────────────┐
                           │       ORDER_ITEMS         │
                           │──────────────────────────│
                           │ id (PK)                  │
                           │ order_id (FK)            │
                           │ product_name             │
                           │ unit_price               │
                           │ quantity                 │
                           │ line_total (COMPUTED)    │
                           └──────────────────────────┘

┌─────────────────────┐
│    OUTBOX_EVENTS    │
│─────────────────────│
│ id (PK)             │
│ aggregate_id        │  ← order.id
│ event_type          │  ← "OrderCreated"
│ payload (JSONB)     │
│ status              │  ← PENDING/PUBLISHED/FAILED
│ created_at          │
│ published_at        │
└─────────────────────┘
```

### Enums

| Enum | Values |
|---|---|
| `OrderStatus` | `PENDING`, `CONFIRMED`, `SHIPPED`, `CANCELLED` |
| `OutboxStatus` | `PENDING`, `PUBLISHED`, `FAILED` |

### Allowed Status Transitions

```
PENDING ──── (Kafka consumer only) ────▶ CONFIRMED
CONFIRMED ──────────────────────────▶ SHIPPED
CONFIRMED ──────────────────────────▶ CANCELLED
PENDING  ── (manual admin override) ──▶ CANCELLED
```

> `PENDING` cannot be manually changed to `CONFIRMED` — that is exclusively owned by the Kafka consumer.

---

## 5. Database Schema

### `customers`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | `gen_random_uuid()` |
| `name` | `VARCHAR(200)` | NOT NULL | — |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Login / contact |
| `address` | `TEXT` | nullable | Delivery address |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | DEFAULT `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Auto-updated |

### `orders`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `customer_id` | `UUID` | FK → customers.id, IDX | NOT NULL |
| `status` | `VARCHAR(20)` | NOT NULL, IDX | DEFAULT `PENDING` |
| `total_value` | `NUMERIC(12,2)` | nullable | Set by Kafka consumer |
| `order_date` | `TIMESTAMPTZ` | NOT NULL | Client-supplied |
| `version` | `BIGINT` | NOT NULL | DEFAULT `0` — `@Version` optimistic lock |
| `deleted` | `BOOLEAN` | NOT NULL | DEFAULT `false` |
| `deleted_at` | `TIMESTAMPTZ` | nullable | Soft delete timestamp |
| `deleted_by` | `UUID` | nullable | Actor who deleted |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | — |

### `order_items`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `order_id` | `UUID` | FK → orders.id CASCADE DELETE, IDX | NOT NULL |
| `product_name` | `VARCHAR(300)` | NOT NULL | Snapshot at order time |
| `unit_price` | `NUMERIC(10,2)` | NOT NULL | CHECK (≥ 0) |
| `quantity` | `INTEGER` | NOT NULL | CHECK (> 0) |
| `line_total` | `NUMERIC(12,2)` | NOT NULL | Computed: `unit_price × quantity` |

### `outbox_events`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | — |
| `aggregate_id` | `UUID` | NOT NULL | The `order.id` |
| `event_type` | `VARCHAR(100)` | NOT NULL | `OrderCreated` |
| `payload` | `JSONB` | NOT NULL | Full event body |
| `status` | `VARCHAR(20)` | NOT NULL, IDX | PENDING / PUBLISHED / FAILED |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | — |
| `published_at` | `TIMESTAMPTZ` | nullable | Set after successful publish |

### Indexes & Constraints Summary

```sql
-- Customers
CREATE UNIQUE INDEX ON customers (email);

-- Orders
CREATE INDEX ON orders (customer_id);
CREATE INDEX ON orders (status) WHERE status = 'PENDING';  -- partial index

-- Order Items
CREATE INDEX ON order_items (order_id);
CHECK (unit_price >= 0);
CHECK (quantity > 0);

-- Outbox
CREATE INDEX ON outbox_events (status) WHERE status = 'PENDING';
```

---

## 6. API Reference

**Base URL:** `/api/v1`  
**Content-Type:** `application/json`  
**Error Format:** RFC 7807 `application/problem+json`

---

### `POST /customers`
Register a new customer.

**Request:**
```json
{
  "name": "Alice Johnson",
  "email": "alice@example.com",
  "address": "12 MG Road, Pune 411001"
}
```

**Response `201 Created`:**
```json
{
  "id": "c9d8e7f6-...",
  "name": "Alice Johnson",
  "email": "alice@example.com",
  "address": "12 MG Road, Pune 411001",
  "createdAt": "2024-01-15T09:00:00Z"
}
```

**Errors:** `400` Validation failed · `409` Email already exists

---

### `GET /customers/{id}`
Get customer details.

**Response `200 OK`:** Customer object.

**Errors:** `404` Not found

---

### `POST /orders`
Create a new order. Returns immediately — async processing handles total calculation.

**Request:**
```json
{
  "customerId": "c9d8e7f6-...",
  "orderDate": "2024-01-15T10:00:00Z",
  "items": [
    {
      "productName": "The Pragmatic Programmer",
      "unitPrice": 45.99,
      "quantity": 2
    },
    {
      "productName": "Clean Architecture",
      "unitPrice": 39.99,
      "quantity": 1
    }
  ]
}
```

**Validation:**
- `customerId` — required, must exist
- `orderDate` — required, ISO 8601 format
- `items` — required, min 1, max 500 items
- `unitPrice` — required, ≥ 0.00
- `quantity` — required, integer > 0

**Response `202 Accepted`:**
```json
{
  "id": "ord-uuid-...",
  "customerId": "c9d8e7f6-...",
  "status": "PENDING",
  "totalValue": null,
  "orderDate": "2024-01-15T10:00:00Z",
  "items": [
    {
      "id": "item-uuid-...",
      "productName": "The Pragmatic Programmer",
      "unitPrice": 45.99,
      "quantity": 2,
      "lineTotal": 91.98
    }
  ],
  "createdAt": "2024-01-15T10:00:01Z"
}
```

> `totalValue` is `null` while order is `PENDING`. Poll `GET /orders/{id}` to check when status becomes `CONFIRMED`.

**Errors:** `400` Validation failed · `404` Customer not found

---

### `GET /orders/{id}`
Get full order details including all items and calculated total.

**Response `200 OK` (after finalization):**
```json
{
  "id": "ord-uuid-...",
  "customerId": "c9d8e7f6-...",
  "status": "CONFIRMED",
  "totalValue": 131.97,
  "orderDate": "2024-01-15T10:00:00Z",
  "version": 1,
  "items": [
    {
      "id": "item-uuid-1",
      "productName": "The Pragmatic Programmer",
      "unitPrice": 45.99,
      "quantity": 2,
      "lineTotal": 91.98
    },
    {
      "id": "item-uuid-2",
      "productName": "Clean Architecture",
      "unitPrice": 39.99,
      "quantity": 1,
      "lineTotal": 39.99
    }
  ],
  "createdAt": "2024-01-15T10:00:01Z",
  "updatedAt": "2024-01-15T10:00:05Z"
}
```

**Errors:** `404` Not found

---

### `GET /customers/{customerId}/orders`
All orders for a customer, paginated.

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | int | No | Default `0` |
| `size` | int | No | Default `20` |
| `status` | enum | No | Filter by order status |
| `sort` | string | No | `orderDate,desc` (default) |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "ord-uuid-...",
      "status": "CONFIRMED",
      "totalValue": 131.97,
      "orderDate": "2024-01-15T10:00:00Z",
      "itemCount": 2
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

> **REST principle:** Orders are a sub-resource of customers (`/customers/{id}/orders`), reflecting the ownership relationship clearly in the URL.

---

### `PATCH /orders/{id}/status`
Manually update an order's status.

**Request:**
```json
{
  "status": "SHIPPED",
  "version": 1
}
```

> `version` field is **required** — used for optimistic locking. Get the current version from `GET /orders/{id}`. If the version doesn't match the DB, returns `409`.

**Allowed transitions via this endpoint:**
- `CONFIRMED` → `SHIPPED`
- `CONFIRMED` → `CANCELLED`

> `PENDING` → `CONFIRMED` is exclusively owned by the Kafka consumer and cannot be triggered manually.

**Response `200 OK`:**
```json
{
  "id": "ord-uuid-...",
  "status": "SHIPPED",
  "totalValue": 131.97,
  "version": 2,
  "updatedAt": "2024-01-16T08:00:00Z"
}
```

**Errors:** `400` Invalid transition · `404` Not found · `409` Version conflict (concurrent update)

---

### `DELETE /orders/{id}`
Soft delete an order.

**Response `204 No Content`**

**Errors:** `404` Not found · `409` Cannot delete a `SHIPPED` order

---

### Standard Error Response (RFC 7807)

```json
{
  "type": "https://api.spry.io/errors/conflict",
  "title": "Resource Conflict",
  "status": 409,
  "detail": "Order was modified concurrently. Please re-fetch and retry.",
  "instance": "/api/v1/orders/ord-uuid-.../status",
  "traceId": "abc123def456"
}
```

---

## 7. Async Flow — Order Finalization

### Problem
Calculating `totalValue` by summing line items is straightforward, but doing it synchronously on every POST would add latency and couple the write path to read aggregations. The `POST /orders` endpoint should return as fast as possible — within ~100ms.

### Solution: 202 Accepted + Kafka Consumer

```
POST /orders
      │
      ▼
┌──────────────────────────────────────────────────┐
│  @Transactional                                   │
│  1. Validate customerId + items                   │
│  2. INSERT orders (status = PENDING)              │
│  3. INSERT order_items (lineTotal computed)       │
│  4. INSERT outbox_events (status = PENDING)       │
│  COMMIT                                           │
└────────────────────────┬─────────────────────────┘
                         │
                         │  returns 202 Accepted immediately
                         ▼
               OutboxRelayService
               @Scheduled (500ms)
                         │
                         │  KafkaTemplate.send("order.created")
                         ▼
               ┌─────────────────┐
               │  order.created  │  Kafka Topic
               └────────┬────────┘
                        │
                        ▼
           OrderFinalizationConsumer
                        │
           ┌────────────▼───────────────┐
           │ 1. SELECT order WHERE      │
           │    id=? AND status=PENDING  │  (idempotency check)
           │ 2. SUM(line_total) from    │
           │    order_items             │
           │ 3. UPDATE orders SET       │
           │    status = CONFIRMED,     │
           │    total_value = SUM,      │
           │    version = version + 1   │
           └────────────────────────────┘
```

### Stuck Order Recovery (Safety Net)

```
@Scheduled every 10 minutes
        │
        ▼
StuckOrderRecoveryScheduler
        │
        │  SELECT * FROM orders
        │  WHERE status = 'PENDING'
        │  AND created_at < now() - 5min
        │
        ▼
  For each stuck order:
        │
        │  kafkaTemplate.send("order.created", orderId)
        │
        ▼
  Finalization consumer reprocesses
  (idempotent — skips if already CONFIRMED)
```

---

## 8. Kafka Design

### Topics

| Topic | Partitions | Purpose |
|---|---|---|
| `order.created` | 3 | Triggers order finalization |
| `order.created.DLQ` | 3 | Failed messages after 3 retry attempts |

### Retry Strategy

```
Message delivered
       │
Attempt 1 → FAIL
       │ wait 1s
Attempt 2 → FAIL
       │ wait 2s
Attempt 3 → FAIL
       │
→ route to order.created.DLQ
→ commit offset (consumer moves on)
→ alert fires: "Order stuck — manual intervention needed"
```

---

## 9. Concurrency & Optimistic Locking

### The Problem
Two threads may try to update the same order simultaneously (e.g. admin marks it `SHIPPED` while the Kafka consumer is confirming it). Without concurrency control, one update silently overwrites the other.

### Solution: `@Version` (Optimistic Locking)

The `version` column on `orders` is annotated with JPA's `@Version`. Every `UPDATE` statement JPA generates includes a `WHERE version = ?` clause:

```sql
UPDATE orders
SET status = 'SHIPPED', version = 2, updated_at = now()
WHERE id = 'ord-uuid-...'
AND version = 1;     ← if version has changed, 0 rows updated → exception
```

If the version in the DB doesn't match the expected version, JPA throws `ObjectOptimisticLockingFailureException`, which the service maps to `HTTP 409 Conflict`.

### Client Flow for PATCH

```
1. GET /orders/{id}              ← fetch current state + version
   Response: { ..., "version": 1 }

2. PATCH /orders/{id}/status     ← include version in request
   Request: { "status": "SHIPPED", "version": 1 }

3a. Success → 200 OK             ← version is now 2
3b. Concurrent update → 409      ← re-fetch from step 1 and retry
```

### Why Not Pessimistic Locking?
Pessimistic locking (`SELECT FOR UPDATE`) holds a DB row lock until the transaction commits. Under concurrent requests, threads queue up waiting for the lock — hurting throughput. Optimistic locking has zero DB-level blocking — it only detects conflicts at commit time, which is far more efficient for low-to-medium conflict rates.

---

## 10. Security

All endpoints are publicly accessible (no authentication required). Input validation is enforced via Jakarta Bean Validation on all request DTOs. No raw SQL — all queries via JPA / JPQL.

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
| `orders.created.total` | Counter | — |
| `orders.finalization.duration` | Histogram | p95 > 30s → alert |
| `orders.pending.stale.count` | Gauge | > 0 for > 10 min → alert |
| `orders.pending.stale.recovered` | Counter | — (informational) |
| `optimistic.lock.retries.total` | Counter | Spike → hot row contention |
| `kafka.consumer.lag` | Gauge | > 500 for > 3 min → alert |

---

## 12. Failure Modes & Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Kafka down at order creation | Order stuck in PENDING | Outbox pattern — event persisted in DB, relay retries |
| Consumer crashes | Order stuck in PENDING | `StuckOrderRecoveryScheduler` re-triggers after 5 min |
| Consumer processes same event twice | Double finalization attempt | Idempotency guard: `if (status != PENDING) return` |
| Concurrent PATCH on same order | One update silently lost | `@Version` optimistic lock — second writer gets 409 |
| Consumer exhausts all retries | Order permanently stuck | DLQ alert fires — ops investigates manually |
| `line_total` calculation drift | Incorrect total | `line_total` computed via `@PrePersist` — single source of truth |

---

## 13. Non-Functional Requirements

| Requirement | Target |
|---|---|
| `POST /orders` p95 response time | < 100ms |
| `GET /orders/{id}` p95 response time | < 150ms |
| Order finalization SLA | Within 30s of creation under normal load |
| Max items per order | 500 (enforced at DTO validation) |
| Soft-deleted order retention | 7 years (financial audit requirement) |
| Service availability | 99.9% |

---

## 14. Key Design Decisions

### Why 202 Accepted instead of 201 Created?
`201 Created` signals the resource is fully created. Since `totalValue` is `null` and `status` is `PENDING`, the order is not yet complete — returning `201` would mislead the client into thinking the order is ready. `202 Accepted` explicitly communicates "we received the request, processing is in progress — poll back for the final state."

### Why is PENDING → CONFIRMED not available via PATCH?
The `PENDING` → `CONFIRMED` transition is exclusively owned by the Kafka finalization consumer. Allowing a manual API call to do the same would bypass the total calculation logic, potentially creating `CONFIRMED` orders with a `null` `totalValue`. Keeping this transition consumer-only enforces the invariant that every `CONFIRMED` order has a valid `totalValue`.

### Why `@Version` and not a DB-level row lock?
Optimistic locking has zero wait time — concurrent requests proceed in parallel and only fail at commit time if their data was stale. Pessimistic locking serialises all concurrent writers, creating a throughput bottleneck. For the order service (where concurrent updates to the same order are infrequent), optimistic locking is the right tradeoff.

### Why a recovery scheduler instead of a DLQ for stuck PENDING orders?
A DLQ preserves failed Kafka messages but doesn't address orders that got stuck because their outbox event was never published (e.g. DB network partition). The recovery scheduler is a complementary safety net that operates at the DB level — it finds orders that are still `PENDING` after 5 minutes and re-publishes their event. This handles all failure modes, not just consumer failures.

### Why `line_total` computed in `@PrePersist` and not in the consumer?
If `line_total` were calculated only in the consumer, a bug in the consumer's math would silently produce wrong totals. By computing it in `@PrePersist` (before persisting the entity), the `order_items` table always has correct line totals — the consumer only needs to `SUM` them. This separates the concern of "what is the line total for this item" from "what is the total for this order."
