# Order Service — Implementation Plan

> **Service:** `order-service` | **Port:** `8082`  
> **Database:** `order_db` (PostgreSQL 15) | **Kafka:** `order.created`, `order.created.DLQ`  
> **Architecture:** Hexagonal (Ports & Adapters)

---

## Table of Contents

1. [Hexagonal Architecture Overview](#1-hexagonal-architecture-overview)
2. [Phase 1 — Project Scaffold](#2-phase-1--project-scaffold)
3. [Phase 2 — Domain Layer](#3-phase-2--domain-layer)
4. [Phase 3 — Application Layer (Ports & Use Cases)](#4-phase-3--application-layer-ports--use-cases)
5. [Phase 4 — Inbound Adapters (Web & Messaging)](#5-phase-4--inbound-adapters-web--messaging)
6. [Phase 5 — Outbound Adapters (Persistence & Messaging)](#6-phase-5--outbound-adapters-persistence--messaging)
7. [Phase 6 — Database & Migrations](#7-phase-6--database--migrations)
8. [Phase 7 — Security](#8-phase-7--security)
9. [Phase 8 — Observability](#9-phase-8--observability)
10. [Phase 9 — Testing](#10-phase-9--testing)
11. [Checklist Summary](#11-checklist-summary)

---

## 1. Hexagonal Architecture Overview

### Core Principle

The domain and application logic sit at the centre and have **zero dependencies** on frameworks, databases, or messaging. All infrastructure concerns are pushed to the outer ring (adapters) and connected through interfaces (ports).

```
┌─────────────────────────────────────────────────────────┐
│                        ADAPTERS                          │
│                                                         │
│   Inbound                          Outbound             │
│  ┌──────────────────┐         ┌──────────────────────┐  │
│  │  REST Controllers│         │  JPA Repositories    │  │
│  │  (Web Adapter)   │         │  (Persistence Adapter│  │
│  │                  │  ┌────┐ │                      │  │
│  │  Kafka Consumer  │─▶│    │◀│  Kafka Producer      │  │
│  │  (Messaging In)  │  │ A  │ │  (Messaging Out)     │  │
│  │                  │  │ P  │ │                      │  │
│  │  Recovery        │  │ P  │ │  OutboxRelayService  │  │
│  │  Scheduler       │  │    │ │  (Messaging Out)     │  │
│  └──────────────────┘  │    │ └──────────────────────┘  │
│                        │ L  │                           │
│                        │ I  │                           │
│                        │ C  │                           │
│                        │ A  │                           │
│                        │ T  │                           │
│                        │ I  │                           │
│                        │ O  │   ┌──────────────────┐    │
│                        │ N  │   │    DOMAIN        │    │
│                        │    │   │                  │    │
│                        │    │   │  Order           │    │
│                        │    │   │  OrderItem       │    │
│                        └────┘   │  Customer        │    │
│                        Ports:   │  OrderStatus     │    │
│                        - In     │  Domain Events   │    │
│                        - Out    └──────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Module Structure

```
order-service/src/main/java/com/spry/order/
│
├── domain/                          ← No framework dependencies
│   ├── model/
│   │   ├── Order.java               ← Aggregate root
│   │   ├── OrderItem.java           ← Value object / child entity
│   │   ├── Customer.java            ← Domain entity
│   │   ├── OrderStatus.java         ← Enum
│   │   └── Money.java               ← Value object for monetary amounts
│   ├── event/
│   │   └── OrderCreatedEvent.java   ← Domain event
│   └── exception/
│       ├── OrderNotFoundException.java
│       ├── CustomerNotFoundException.java
│       ├── InvalidStatusTransitionException.java
│       └── OrderNotDeletableException.java
│
├── application/                     ← Orchestrates domain, owns ports
│   ├── port/
│   │   ├── in/                      ← Inbound ports (use case interfaces)
│   │   │   ├── CreateOrderUseCase.java
│   │   │   ├── GetOrderUseCase.java
│   │   │   ├── ListCustomerOrdersUseCase.java
│   │   │   ├── UpdateOrderStatusUseCase.java
│   │   │   ├── DeleteOrderUseCase.java
│   │   │   ├── CreateCustomerUseCase.java
│   │   │   └── GetCustomerUseCase.java
│   │   └── out/                     ← Outbound ports (infrastructure interfaces)
│   │       ├── LoadOrderPort.java
│   │       ├── SaveOrderPort.java
│   │       ├── LoadCustomerPort.java
│   │       ├── SaveCustomerPort.java
│   │       └── PublishOrderEventPort.java
│   └── service/                     ← Use case implementations
│       ├── OrderService.java
│       └── CustomerService.java
│
└── adapter/
    ├── in/
    │   ├── web/                     ← Inbound: REST
    │   │   ├── OrderController.java
    │   │   ├── CustomerController.java
    │   │   └── dto/
    │   │       ├── CreateOrderRequest.java
    │   │       ├── OrderResponse.java
    │   │       ├── UpdateStatusRequest.java
    │   │       └── ...
    │   └── messaging/               ← Inbound: Kafka + Scheduler
    │       ├── OrderFinalizationConsumer.java
    │       └── StuckOrderRecoveryScheduler.java
    └── out/
        ├── persistence/             ← Outbound: JPA
        │   ├── OrderPersistenceAdapter.java   ← implements LoadOrderPort, SaveOrderPort
        │   ├── CustomerPersistenceAdapter.java
        │   ├── entity/
        │   │   ├── OrderJpaEntity.java
        │   │   ├── OrderItemJpaEntity.java
        │   │   └── CustomerJpaEntity.java
        │   ├── repository/
        │   │   ├── OrderJpaRepository.java
        │   │   └── CustomerJpaRepository.java
        │   └── mapper/
        │       ├── OrderPersistenceMapper.java
        │       └── CustomerPersistenceMapper.java
        └── messaging/               ← Outbound: Kafka
            ├── KafkaOrderEventAdapter.java    ← implements PublishOrderEventPort
            └── OutboxRelayService.java
```

### Dependency Rule

```
domain        ←  no dependencies
application   ←  depends only on domain
adapter/in    ←  depends on application (inbound ports)
adapter/out   ←  depends on application (outbound ports) + domain
```

> Spring annotations (`@Service`, `@Component`, `@Transactional`) are allowed in the `application` and `adapter` layers — **never** in `domain`.

---

## 2. Phase 1 — Project Scaffold

### Tasks

- [ ] Create `order-service` Maven module under `spry-backend/`; reference parent POM
- [ ] Add `shared-lib` dependency for `JwtAuthFilter`, `GlobalExceptionHandler`
- [ ] Configure `application.yml`: port `8082`, datasource (`order_db`), Kafka bootstrap
- [ ] Add `order_db` to Docker Compose
- [ ] Set up package structure: `domain`, `application`, `adapter` (as above)
- [ ] Configure MapStruct for `adapter/out/persistence/mapper` and `adapter/in/web/dto`

---

## 3. Phase 2 — Domain Layer

> Pure Java — no Spring, no JPA, no Kafka imports anywhere in this layer.

### `Order` (Aggregate Root)

```java
public class Order {
    private OrderId id;
    private CustomerId customerId;
    private OrderStatus status;
    private Money totalValue;          // null until CONFIRMED
    private LocalDateTime orderDate;
    private Long version;              // optimistic lock version — carried through, not managed here
    private List<OrderItem> items;
    private boolean deleted;
    private Instant deletedAt;
    private UserId deletedBy;

    public static Order create(CustomerId customerId, LocalDateTime orderDate, List<OrderItem> items) {
        // factory method — validates and sets initial status = PENDING
    }

    public void confirm(Money total) {
        // validates current status == PENDING, sets CONFIRMED + totalValue
    }

    public void transitionTo(OrderStatus next, UserId actor) {
        // validates allowed transitions, throws InvalidStatusTransitionException
    }

    public void softDelete(UserId actor) {
        // rejects if SHIPPED, sets deleted=true
    }
}
```

### `OrderItem` (Value Object)

```java
public class OrderItem {
    private OrderItemId id;
    private String productName;
    private Money unitPrice;
    private int quantity;

    public Money lineTotal() {
        return unitPrice.multiply(quantity);  // computed, not stored in domain
    }
}
```

### `Customer` (Domain Entity)

```java
public class Customer {
    private CustomerId id;
    private String name;
    private Email email;
    private String address;
}
```

### `Money` (Value Object)

```java
public record Money(BigDecimal amount) {
    public Money { if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(); }
    public Money multiply(int qty) { return new Money(amount.multiply(BigDecimal.valueOf(qty))); }
    public Money add(Money other) { return new Money(amount.add(other.amount)); }
}
```

### `OrderCreatedEvent` (Domain Event)

```java
public record OrderCreatedEvent(OrderId orderId, CustomerId customerId, Instant occurredAt) {}
```

### Enums

| Enum | Values |
|---|---|
| `OrderStatus` | `PENDING`, `CONFIRMED`, `SHIPPED`, `CANCELLED` |

### Status Transition Rules (enforced in `Order.transitionTo`)

```
PENDING  ──── consumer-only ────▶ CONFIRMED      (via Order.confirm())
CONFIRMED ──────────────────────▶ SHIPPED
CONFIRMED ──────────────────────▶ CANCELLED
PENDING  ────────────────────────▶ CANCELLED      (admin override)
SHIPPED / CANCELLED              → terminal, no further transitions
```

---

## 4. Phase 3 — Application Layer (Ports & Use Cases)

### Inbound Ports (Use Case Interfaces)

```java
// adapter/in calls these — defines what the application can do
public interface CreateOrderUseCase {
    OrderResult createOrder(CreateOrderCommand command);
}

public interface UpdateOrderStatusUseCase {
    OrderResult updateStatus(UpdateOrderStatusCommand command);
}

public interface GetOrderUseCase {
    Order getOrder(OrderId id, UserId requesterId, UserRole role);
}

public interface ListCustomerOrdersUseCase {
    Page<Order> listOrders(CustomerId customerId, UserId requesterId, UserRole role, Pageable pageable);
}

public interface DeleteOrderUseCase {
    void deleteOrder(OrderId id, UserId actorId);
}

public interface CreateCustomerUseCase {
    Customer createCustomer(CreateCustomerCommand command);
}

public interface GetCustomerUseCase {
    Customer getCustomer(CustomerId id, UserId requesterId, UserRole role);
}
```

### Outbound Ports (Infrastructure Interfaces)

```java
// application calls these — persistence and messaging are behind these interfaces
public interface LoadOrderPort {
    Optional<Order> loadOrder(OrderId id);
    Page<Order> loadOrdersByCustomer(CustomerId customerId, Pageable pageable);
    List<Order> loadStuckPendingOrders(Instant olderThan);
}

public interface SaveOrderPort {
    Order saveOrder(Order order);
}

public interface LoadCustomerPort {
    Optional<Customer> loadCustomer(CustomerId id);
    boolean existsByEmail(Email email);
}

public interface SaveCustomerPort {
    Customer saveCustomer(Customer customer);
}

public interface PublishOrderEventPort {
    void publish(OrderCreatedEvent event);
}
```

### Commands (Input Models for Use Cases)

```java
public record CreateOrderCommand(
    CustomerId customerId, LocalDateTime orderDate,
    List<OrderItemCommand> items, UserId requesterId) {}

public record UpdateOrderStatusCommand(
    OrderId orderId, OrderStatus newStatus, Long version, UserId actorId) {}

public record CreateCustomerCommand(String name, Email email, String address) {}
```

### `OrderService` (implements use cases)

```java
@Service
@Transactional
public class OrderService implements CreateOrderUseCase, GetOrderUseCase,
        ListCustomerOrdersUseCase, UpdateOrderStatusUseCase, DeleteOrderUseCase {

    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final LoadCustomerPort loadCustomerPort;
    private final PublishOrderEventPort publishOrderEventPort;

    @Override
    public OrderResult createOrder(CreateOrderCommand command) {
        // 1. loadCustomerPort.loadCustomer() → 404 if absent
        // 2. Order.create() — domain factory
        // 3. saveOrderPort.saveOrder(order)
        // 4. publishOrderEventPort.publish(new OrderCreatedEvent(...))  ← writes to outbox
        // 5. return result (202)
    }

    @Override
    public OrderResult updateStatus(UpdateOrderStatusCommand command) {
        // 1. loadOrderPort.loadOrder() → 404 if absent
        // 2. order.transitionTo(newStatus) — domain enforces rules
        // 3. saveOrderPort.saveOrder(order) — catches OptimisticLockException → 409
        // 4. return result
    }
}
```

### `CustomerService` (implements use cases)

```java
@Service
@Transactional
public class CustomerService implements CreateCustomerUseCase, GetCustomerUseCase {
    private final SaveCustomerPort saveCustomerPort;
    private final LoadCustomerPort loadCustomerPort;

    @Override
    public Customer createCustomer(CreateCustomerCommand command) {
        // existsByEmail check → 409 if duplicate
        // Customer.create() + saveCustomerPort.saveCustomer()
    }
}
```

---

## 5. Phase 4 — Inbound Adapters (Web & Messaging)

### Web Adapter — `OrderController`

- [ ] Maps HTTP request DTOs → commands → calls inbound port (use case)
- [ ] Maps domain objects → response DTOs (MapStruct)
- [ ] No business logic — pure translation layer

```java
@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    // ...

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal JwtUser user) {

        var command = new CreateOrderCommand(
            new CustomerId(request.customerId()),
            request.orderDate(),
            request.items().stream().map(this::toCommand).toList(),
            user.userId()
        );
        var result = createOrderUseCase.createOrder(command);
        return ResponseEntity.accepted().body(orderWebMapper.toResponse(result));
    }
}
```

### Web Adapter — `CustomerController`

- [ ] `POST /customers` → `CreateCustomerUseCase`
- [ ] `GET /customers/{id}` → `GetCustomerUseCase`
- [ ] `GET /customers/{customerId}/orders` → `ListCustomerOrdersUseCase`

### Web DTOs (`adapter/in/web/dto`)

- [ ] `CreateOrderRequest`, `OrderResponse`, `OrderSummaryResponse`
- [ ] `UpdateStatusRequest` (with `version` field)
- [ ] `CreateCustomerRequest`, `CustomerResponse`
- [ ] `OrderWebMapper` (MapStruct): command ↔ request, domain ↔ response

### Messaging Adapter — `OrderFinalizationConsumer`

```java
@Component
public class OrderFinalizationConsumer {
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;  // ← calls application port

    @KafkaListener(topics = "order.created", groupId = "order-finalization")
    public void consume(OrderCreatedEvent event) {
        // idempotency: skip if order already CONFIRMED (checked in use case)
        // calls updateOrderStatusUseCase with CONFIRMED transition
    }
}
```

### Messaging Adapter — `StuckOrderRecoveryScheduler`

```java
@Component
public class StuckOrderRecoveryScheduler {
    private final LoadOrderPort loadOrderPort;         // ← outbound port
    private final PublishOrderEventPort publishPort;   // ← outbound port

    @Scheduled(fixedDelay = 600_000)
    public void recover() {
        var threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        loadOrderPort.loadStuckPendingOrders(threshold)
            .forEach(order -> publishPort.publish(new OrderCreatedEvent(order.getId(), ...)));
    }
}
```

### Validation Annotations on Web DTOs

| Field | Constraint |
|---|---|
| `customerId` | `@NotNull` |
| `orderDate` | `@NotNull` (ISO 8601) |
| `items` | `@NotEmpty @Size(max=500)` |
| `unitPrice` | `@NotNull @DecimalMin("0.00")` |
| `quantity` | `@NotNull @Min(1)` |
| `version` | `@NotNull` (required in PATCH status request) |

---

## 6. Phase 5 — Outbound Adapters (Persistence & Messaging)

### Persistence Adapter — `OrderPersistenceAdapter`

Implements `LoadOrderPort` and `SaveOrderPort`. Translates between domain `Order` and JPA `OrderJpaEntity`.

```java
@Component
public class OrderPersistenceAdapter implements LoadOrderPort, SaveOrderPort {
    private final OrderJpaRepository jpaRepository;
    private final OrderPersistenceMapper mapper;

    @Override
    public Optional<Order> loadOrder(OrderId id) {
        return jpaRepository.findByIdAndDeletedFalse(id.value())
            .map(mapper::toDomain);
    }

    @Override
    public Order saveOrder(Order order) {
        var entity = mapper.toJpaEntity(order);
        try {
            return mapper.toDomain(jpaRepository.save(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentOrderModificationException(order.getId());
        }
    }
}
```

### JPA Entities (`adapter/out/persistence/entity`)

> JPA entities are infrastructure details — they live only in the adapter layer.

- [ ] `OrderJpaEntity` — `@Entity @Table(name="orders")`
  - `@Version private Long version` — optimistic lock lives here, not in domain
  - All columns per DB schema
- [ ] `OrderItemJpaEntity` — `@Entity @Table(name="order_items")`
  - `@PrePersist` sets `lineTotal = unitPrice × quantity`
- [ ] `CustomerJpaEntity` — `@Entity @Table(name="customers")`
- [ ] `OutboxEventJpaEntity` — `@Entity @Table(name="outbox_events")`

### JPA Repositories (`adapter/out/persistence/repository`)

- [ ] `OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID>`:
  - `findByIdAndDeletedFalse(UUID)`
  - `findByCustomerIdAndDeletedFalse(UUID, Pageable)`
  - `findByStatusAndCreatedAtBefore(String status, Instant threshold)`
- [ ] `CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID>`:
  - `existsByEmail(String)`
- [ ] `OutboxEventJpaRepository`:
  - `findByStatusOrderByCreatedAtAsc(String status, Pageable)`

### Persistence Mappers (`adapter/out/persistence/mapper`)

- [ ] `OrderPersistenceMapper` (MapStruct): `Order` ↔ `OrderJpaEntity`
- [ ] `CustomerPersistenceMapper` (MapStruct): `Customer` ↔ `CustomerJpaEntity`

### Messaging Adapter — `KafkaOrderEventAdapter`

Implements `PublishOrderEventPort`. Writes to `outbox_events` table (outbox pattern).

```java
@Component
public class KafkaOrderEventAdapter implements PublishOrderEventPort {
    private final OutboxEventJpaRepository outboxRepository;

    @Override
    @Transactional   // called inside OrderService @Transactional — same transaction
    public void publish(OrderCreatedEvent event) {
        var outboxEntity = OutboxEventJpaEntity.from(event);  // status = PENDING
        outboxRepository.save(outboxEntity);
        // actual Kafka send happens via OutboxRelayService (scheduled poller)
    }
}
```

### Messaging Adapter — `OutboxRelayService`

```java
@Component
public class OutboxRelayService {
    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, 50))
            .forEach(event -> {
                kafkaTemplate.send("order.created", event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) event.markPublished();
                        else event.markFailed();
                    });
            });
    }
}
```

### Kafka Config

```yaml
spring.kafka.producer:
  acks: all
  retries: 3
  properties:
    enable.idempotence: true
```

### DLQ & Retry Config

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (r, e) -> new TopicPartition(r.topic() + ".DLQ", r.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
}
```

---

## 7. Phase 6 — Database & Migrations

### Migrations

- [ ] `V001__create_customers.sql`
  - `id UUID PK DEFAULT gen_random_uuid()`, `name VARCHAR(200) NOT NULL`, `email VARCHAR(255) NOT NULL UNIQUE`, `address TEXT`, audit fields
  - `CREATE UNIQUE INDEX ON customers (email)`
- [ ] `V002__create_orders.sql`
  - `id UUID PK`, `customer_id UUID FK → customers.id`, `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`
  - `total_value NUMERIC(12,2) NULL` — set by consumer; nullable until confirmed
  - `order_date TIMESTAMPTZ NOT NULL`, `version BIGINT NOT NULL DEFAULT 0`
  - Soft-delete fields: `deleted BOOLEAN DEFAULT false`, `deleted_at`, `deleted_by`
  - `CREATE INDEX ON orders (customer_id)`
  - `CREATE INDEX ON orders (status) WHERE status = 'PENDING'` (partial)
- [ ] `V003__create_order_items.sql`
  - `id UUID PK`, `order_id UUID FK → orders.id CASCADE DELETE`, `product_name VARCHAR(300)`, `unit_price NUMERIC(10,2)`, `quantity INTEGER`, `line_total NUMERIC(12,2)`
  - `CHECK (unit_price >= 0)`, `CHECK (quantity > 0)`
  - `CREATE INDEX ON order_items (order_id)`
- [ ] `V004__create_outbox_events.sql`
  - `aggregate_id UUID NOT NULL`, `event_type VARCHAR(100)`, `payload JSONB NOT NULL`, `status VARCHAR(20) NOT NULL`
  - `CREATE INDEX ON outbox_events (status) WHERE status = 'PENDING'`

---

## 8. Phase 7 — Security

### Spring Security Config

- [ ] `SecurityFilterChain`: add `JwtAuthFilter` (from `shared-lib`) before `UsernamePasswordAuthenticationFilter`
- [ ] Permit `/actuator/**` unauthenticated
- [ ] All `/api/v1/**` require valid JWT
- [ ] `JwtUser` principal injected into controllers via `@AuthenticationPrincipal`

### Role Guards

| Endpoint | CUSTOMER | ADMIN |
|---|---|---|
| `POST /customers` | ❌ | ✅ |
| `GET /customers/{id}` | ✅ (own) | ✅ (any) |
| `POST /orders` | ✅ | ✅ |
| `GET /orders/{id}` | ✅ (own) | ✅ (any) |
| `GET /customers/{id}/orders` | ✅ (own) | ✅ (any) |
| `PATCH /orders/{id}/status` | ❌ | ✅ |
| `DELETE /orders/{id}` | ❌ | ✅ |

### Security Invariants

- [ ] `customerId` in `POST /orders` validated against JWT `sub` in `OrderService` — customer cannot order for others
- [ ] Ownership check in `GetOrderUseCase` and `ListCustomerOrdersUseCase` — `403` if mismatch
- [ ] `totalValue` and financial data excluded from all application logs

---

## 9. Phase 8 — Observability

### Micrometer Metrics

- [ ] `Counter`: `orders.created.total`
- [ ] `Histogram`: `orders.finalization.duration` — p95 alert > 30s
- [ ] `Gauge`: `orders.pending.stale.count` — alert if > 0 for > 10 min
- [ ] `Counter`: `orders.pending.stale.recovered`
- [ ] `Counter`: `optimistic.lock.retries.total` — spike indicates hot row contention
- [ ] `Gauge`: `kafka.consumer.lag` — alert if > 500 for > 3 min

### Actuator & Logging

```yaml
management:
  endpoints.web.exposure.include: health, prometheus
  endpoint.health.show-details: always
```

- [ ] MDC fields in all log entries: `traceId`, `spanId`, `userId`, `requestId` (set in `JwtAuthFilter`)
- [ ] Log order IDs but never `totalValue` or `unitPrice`

---

## 10. Phase 9 — Testing

### Hexagonal Architecture Test Strategy

| Layer | Test type | What to test |
|---|---|---|
| Domain | Plain unit tests (no Spring) | Business rules, transitions, `Money` math, `Order.create()` |
| Application services | Unit tests (Mockito) | Use case orchestration, port interactions |
| Web adapters | `@WebMvcTest` (slice) | Request mapping, validation, auth, DTO mapping |
| Persistence adapters | `@DataJpaTest` (slice) | JPA queries, optimistic lock, mapper correctness |
| Kafka adapters | Embedded Kafka tests | Consumer idempotency, DLQ routing |
| Full flow | `@SpringBootTest` + Testcontainers | End-to-end: POST → outbox → relay → CONFIRMED |

### Domain Tests (no Spring context)

```java
class OrderTest {
    @Test void confirm_transitions_pending_to_confirmed() { ... }
    @Test void confirm_throws_when_already_confirmed() { ... }
    @Test void transitionTo_ship_rejects_pending_order() { ... }
    @Test void softDelete_rejects_shipped_order() { ... }
}

class MoneyTest {
    @Test void rejects_negative_amount() { ... }
    @Test void lineTotal_is_unit_price_times_quantity() { ... }
}
```

### Application Service Tests (Mockito)

- [ ] `OrderServiceTest`:
  - `createOrder`: customer not found → `CustomerNotFoundException`
  - `createOrder`: publishes `OrderCreatedEvent` via `PublishOrderEventPort`
  - `updateStatus`: `PENDING → CONFIRMED` rejected (domain rule)
  - `updateStatus`: `ConcurrentOrderModificationException` → surfaces to controller as `409`
- [ ] `CustomerServiceTest`: duplicate email → exception

### Web Adapter Tests (`@WebMvcTest`)

- [ ] `OrderControllerTest`: `POST /orders` → 202, missing fields → 400, unauth → 401
- [ ] `CustomerControllerTest`: `POST /customers` as CUSTOMER → 403

### Persistence Adapter Tests (`@DataJpaTest`)

- [ ] `OrderPersistenceAdapterTest`: save + reload roundtrip, soft-delete filter, stale query
- [ ] Optimistic lock: two saves with same version → `ObjectOptimisticLockingFailureException`

### Kafka / Integration Tests

- [ ] `OrderFinalizationConsumerTest` (embedded Kafka): produce → consumer confirms order
- [ ] Idempotency: produce same event twice → order confirmed only once
- [ ] DLQ: consumer throws → after 3 retries → message in `order.created.DLQ`
- [ ] `OrderControllerIT` (`@SpringBootTest` + Testcontainers): full flow POST → CONFIRMED

### Concurrency Tests

- [ ] Two threads `PATCH` same order simultaneously → one `200`, one `409`
- [ ] Recovery scheduler + consumer fire simultaneously → idempotency holds

---

## 11. Checklist Summary

### Domain Layer
- [ ] `Order` aggregate root with `create()`, `confirm()`, `transitionTo()`, `softDelete()`
- [ ] `OrderItem` value object with `lineTotal()` computation
- [ ] `Customer` entity, `Money` value object, `Email` value object
- [ ] `OrderCreatedEvent` domain event
- [ ] Domain exceptions (`OrderNotFoundException`, `InvalidStatusTransitionException`, etc.)

### Application Layer
- [ ] Inbound ports: `CreateOrderUseCase`, `GetOrderUseCase`, `ListCustomerOrdersUseCase`, `UpdateOrderStatusUseCase`, `DeleteOrderUseCase`, `CreateCustomerUseCase`, `GetCustomerUseCase`
- [ ] Outbound ports: `LoadOrderPort`, `SaveOrderPort`, `LoadCustomerPort`, `SaveCustomerPort`, `PublishOrderEventPort`
- [ ] Commands: `CreateOrderCommand`, `UpdateOrderStatusCommand`, `CreateCustomerCommand`
- [ ] `OrderService` (implements order use cases)
- [ ] `CustomerService` (implements customer use cases)

### Inbound Adapters
- [ ] `OrderController` + `CustomerController` (web)
- [ ] Web DTOs + `OrderWebMapper` (MapStruct)
- [ ] `OrderFinalizationConsumer` (Kafka inbound)
- [ ] `StuckOrderRecoveryScheduler` (scheduled inbound)

### Outbound Adapters
- [ ] `OrderPersistenceAdapter` (implements `LoadOrderPort`, `SaveOrderPort`)
- [ ] `CustomerPersistenceAdapter` (implements `LoadCustomerPort`, `SaveCustomerPort`)
- [ ] `OrderJpaEntity`, `OrderItemJpaEntity` with `@Version`, `@PrePersist lineTotal`
- [ ] `OrderPersistenceMapper`, `CustomerPersistenceMapper` (MapStruct)
- [ ] `KafkaOrderEventAdapter` (implements `PublishOrderEventPort`, writes to outbox)
- [ ] `OutboxRelayService` (`@Scheduled` 500ms, polls outbox → Kafka)
- [ ] DLQ config (3 retries, 1s backoff, `DeadLetterPublishingRecoverer`)

### Infrastructure
- [ ] Liquibase migrations (V001–V004)
- [ ] Kafka producer config (acks=all, idempotent)
- [ ] JWT security filter chain + role guards
- [ ] Micrometer metrics (all 6)
- [ ] Actuator health + Prometheus
- [ ] Structured logging (MDC: traceId, spanId, userId)

### Testing
- [ ] Domain unit tests (no Spring)
- [ ] Application service tests (Mockito)
- [ ] `@WebMvcTest` slice tests for controllers
- [ ] `@DataJpaTest` slice tests for persistence adapters
- [ ] Embedded Kafka tests for consumer + DLQ
- [ ] Optimistic lock concurrency test
- [ ] `@SpringBootTest` end-to-end integration test
- [ ] Test coverage ≥ 80% on application + domain layers
