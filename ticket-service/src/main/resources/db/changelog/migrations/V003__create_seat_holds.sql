--liquibase formatted sql

--changeset ticket-service:V003__create_seat_holds
CREATE TABLE seat_holds (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID        NOT NULL REFERENCES events(id),
    user_id     UUID,
    seat_count  INTEGER     NOT NULL CHECK (seat_count >= 1),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_seat_holds_event_status_expires ON seat_holds (event_id, status, expires_at);

CREATE UNIQUE INDEX idx_seat_holds_one_active_per_user_event
    ON seat_holds (user_id, event_id)
    WHERE status = 'ACTIVE' AND user_id IS NOT NULL;
