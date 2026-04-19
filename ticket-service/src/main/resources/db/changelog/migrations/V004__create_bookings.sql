--liquibase formatted sql

--changeset ticket-service:V004__create_bookings
CREATE TABLE bookings (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id     UUID        NOT NULL UNIQUE REFERENCES seat_holds(id),
    event_id    UUID        NOT NULL REFERENCES events(id),
    user_id     UUID,
    seat_count  INTEGER     NOT NULL,
    status      VARCHAR(20) NOT NULL,
    deleted     BOOLEAN     NOT NULL DEFAULT false,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_bookings_no_double_booking
    ON bookings (user_id, event_id)
    WHERE deleted = false AND user_id IS NOT NULL;
