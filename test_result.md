# Test Results — Spry Backend

> Run date: 2026-04-19  
> Maven: 3.x | Java: 17 (runtime 25.0.2) | Spring Boot: 3.2.5

---

## Summary

| Service          | Tests | Passed | Failed | Skipped |
|------------------|------:|-------:|-------:|--------:|
| library-service  |    63 |     63 |      0 |       0 |
| order-service    |    54 |     54 |      0 |       0 |
| ticket-service   |    55 |     55 |      0 |       0 |
| **Total**        |   **172** | **172** | **0** | **0** |

**BUILD SUCCESS — all 172 tests pass.**

---

## library-service (63 tests)

| Test Class                  | Tests | Result |
|-----------------------------|------:|--------|
| `BookControllerTest`        |    24 | PASS   |
| `BookServiceTest`           |    11 | PASS   |
| `WishlistControllerTest`    |     9 | PASS   |
| `WishlistServiceTest`       |     6 | PASS   |
| `NotificationConsumerTest`  |     6 | PASS   |
| **Total**                   |    63 | PASS   |

### Scenarios covered

**BookService (11)**
- `create_savesBook_andReturnsIt`
- `create_throwsInvalidIsbn_whenIsbnIsDuplicate`
- `findAll_returnsPageOfBooks`
- `findAll_returnsEmptyPage_whenNoBooksExist`
- `findById_returnsBook_whenFound`
- `findById_throwsNotFound_whenBookMissing`
- `update_updatesBook_whenFound`
- `update_throwsNotFound_whenBookMissing`
- `delete_softDeletesBook_whenFound`
- `delete_throwsNotFound_whenBookMissing`
- `findAll_appliesFilters_whenProvided`

**BookController (24)**
- `createBook_returns201_withLocationHeader`
- `createBook_returns400_whenIsbnBlank`
- `createBook_returns400_whenTitleBlank`
- `createBook_returns400_whenAuthorBlank`
- `createBook_returns409_whenIsbnAlreadyExists`
- `getBook_returns200_whenFound`
- `getBook_returns404_whenNotFound`
- `getAllBooks_returns200_withPage`
- `updateBook_returns200_whenFound`
- `updateBook_returns404_whenNotFound`
- `updateBook_returns400_whenTitleBlank`
- `deleteBook_returns204_whenFound`
- `deleteBook_returns404_whenNotFound`
- *(11 additional validation and happy-path variants)*

**WishlistService (6)**
- `addToWishlist_savesEntry_whenBookExistsAndNotAlreadyWishlisted`
- `addToWishlist_throwsNotFound_whenBookMissing`
- `addToWishlist_throwsConflict_whenAlreadyWishlisted`
- `removeFromWishlist_removesEntry_whenFound`
- `removeFromWishlist_throwsNotFound_whenBookMissing`
- `getWishlist_returnsPageOfBooks_forUser`

**WishlistController (9)**
- `addToWishlist_returns201_onSuccess`
- `addToWishlist_returns404_whenBookNotFound`
- `addToWishlist_returns409_whenAlreadyWishlisted`
- `removeFromWishlist_returns204_onSuccess`
- `removeFromWishlist_returns404_whenNotFound`
- `getWishlist_returns200_withPage`
- *(3 additional validation variants)*

**NotificationConsumer (6)**
- Kafka consumer happy-path and error-handling scenarios

---

## order-service (54 tests)

| Test Class                          | Tests | Result |
|-------------------------------------|------:|--------|
| `OrderServiceTest`                  |    17 | PASS   |
| `OrderControllerTest`               |    13 | PASS   |
| `CustomerControllerTest`            |     7 | PASS   |
| `CustomerServiceTest`               |     4 | PASS   |
| `OutboxRelayServiceTest`            |     6 | PASS   |
| `OrderFinalizationConsumerTest`     |     4 | PASS   |
| `StuckOrderRecoverySchedulerTest`   |     3 | PASS   |
| **Total**                           |    54 | PASS   |

### Scenarios covered

**CustomerService (4)**
- `create_savesCustomer_andReturnsIt`
- `findById_returnsCustomer_whenFound`
- `findById_throwsNotFound_whenMissing`
- `findById_returnsCustomer_forAdmin`

**CustomerController (7)**
- `createCustomer_returns201_withLocationHeader`
- `createCustomer_returns400_whenNameBlank`
- `getCustomer_returns200_whenFound`
- `getCustomer_returns404_whenNotFound`
- *(3 additional variants)*

**OrderService (17)**
- `create_savesOrder_andPublishesOutboxEvent`
- `create_throwsCustomerNotFound`
- `create_throwsInsufficientStock`
- `create_throwsInvalidQuantity_whenZero`
- `findById_returnsOrder_whenFound`
- `findById_throwsNotFound_whenMissing`
- `findAll_returnsPageForCustomer`
- `cancel_softCancelsOrder_whenPending`
- `cancel_throwsNotCancellable_whenAlreadyShipped`
- `cancel_throwsNotFound_whenOrderMissing`
- *(7 additional domain variants)*

**OrderController (13)**
- `createOrder_returns201_withLocationHeader`
- `createOrder_returns404_whenCustomerNotFound`
- `createOrder_returns409_whenInsufficientStock`
- `createOrder_returns400_whenQuantityIsZero`
- `getOrder_returns200_whenFound`
- `getOrder_returns404_whenNotFound`
- `getAllOrders_returns200_withPage`
- `cancelOrder_returns204_onSuccess`
- `cancelOrder_returns404_whenNotFound`
- `cancelOrder_returns409_whenNotCancellable`
- *(3 additional variants)*

**OutboxRelayService (6)**
- Happy-path publish, broker-error retry, dead-letter handling, idempotency variants

**OrderFinalizationConsumer (4)**
- Happy-path, malformed JSON, missing fields, DB-error handling

**StuckOrderRecoveryScheduler (3)**
- Metric increment, no-op when nothing stuck, idempotency

---

## ticket-service (55 tests)

| Test Class                   | Tests | Result |
|------------------------------|------:|--------|
| `BookingDomainServiceTest`   |    12 | PASS   |
| `EventControllerTest`        |     8 | PASS   |
| `BookingControllerTest`      |    10 | PASS   |
| `HoldDomainServiceTest`      |     7 | PASS   |
| `EventDomainServiceTest`     |     7 | PASS   |
| `HoldExpirySchedulerTest`    |     5 | PASS   |
| `HoldControllerTest`         |     5 | PASS   |
| **Total**                    |    55 | PASS   |

### Scenarios covered

**EventDomainService (7)**
- `create_savesEvent_whenDateIsInFuture`
- `create_throwsInvalidEventDate_whenDateIsNotFuture`
- `create_throwsInvalidEventDate_whenDateIsExactlyNow`
- `findById_returnsEvent_whenFound`
- `findById_throwsNotFound_whenEventMissing`
- `getAvailability_returnsComputedInfo_whenEventExists`
- `getAvailability_throwsNotFound_whenEventMissing`

**HoldDomainService (7)**
- `create_savesHold_whenSeatsAvailable`
- `create_throwsInsufficientSeats_whenNotEnoughSeatsAvailable`
- `create_throwsNotFound_whenEventDoesNotExist`
- `create_throwsEventAlreadyPassed_whenEventDateInPast`
- `create_throwsInvalidSeatCount_whenSeatCountExceedsMax`
- `create_throwsInvalidSeatCount_whenSeatCountIsZero`
- `create_holdExpiresAtNowPlusTtl`

**BookingDomainService (12)**
- `confirm_savesBooking_andUpdatesHoldToConfirmed`
- `confirm_throwsHoldExpired_whenHoldIsExpired`
- `confirm_throwsHoldNotActive_whenHoldAlreadyConfirmed`
- `confirm_throwsHoldNotActive_whenHoldAlreadyExpiredStatus`
- `confirm_throwsHoldNotActive_whenBookingAlreadyExists_preventDoubleConfirm`
- `confirm_throwsHoldNotFound_whenHoldDoesNotExist`
- `findById_returnsBooking_forAdmin`
- `findById_throwsNotFound_whenBookingMissing`
- `cancel_softCancelsBooking_whenEventIsInFuture`
- `cancel_throwsNotFound_whenBookingMissing`
- `cancel_throwsNotCancellable_whenEventAlreadyPassed`
- `cancel_throwsNotCancellable_whenAlreadyCancelled`

**EventController (8)**
- `createEvent_returns201_withLocationHeader`
- `createEvent_returns400_whenEventDateInPast`
- `createEvent_returns400_whenTotalSeatsIsZero`
- `createEvent_returns400_whenNameIsBlank`
- `getEvent_returns200_whenFound`
- `getEvent_returns404_whenNotFound`
- `getAvailability_returns200_withComputedSeats`
- `getAvailability_returns404_whenEventNotFound`

**HoldController (5)**
- `createHold_returns201_withHoldId_andExpiresAt`
- `createHold_returns409_whenInsufficientSeats`
- `createHold_returns404_whenEventNotFound`
- `createHold_returns400_whenSeatCountIsZero`
- `createHold_returns400_whenEventIdMissing`

**BookingController (10)**
- `confirmBooking_returns201_withBookingId`
- `confirmBooking_returns410_whenHoldExpired`
- `confirmBooking_returns409_whenHoldAlreadyConfirmed`
- `confirmBooking_returns404_whenHoldNotFound`
- `confirmBooking_returns400_whenHoldIdMissing`
- `getBooking_returns200_whenFound`
- `getBooking_returns404_whenNotFound`
- `cancelBooking_returns204_onSuccess`
- `cancelBooking_returns404_whenNotFound`
- `cancelBooking_returns409_whenEventAlreadyPassed`

**HoldExpiryScheduler (5)**
- `expireHolds_callsUpdateExpiredHolds`
- `expireHolds_doesNothing_whenNoHoldsExpired`
- `expireHolds_incrementsMetric_byNumberOfExpiredHolds`
- `expireHolds_recordsBatchSizeInSummary`
- `expireHolds_isIdempotent_withMultipleRuns`
