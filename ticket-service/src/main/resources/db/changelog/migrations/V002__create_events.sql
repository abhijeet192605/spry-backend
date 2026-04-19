--liquibase formatted sql

--changeset ticket-service:V002__create_events
CREATE TABLE events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(300) NOT NULL,
    event_date  TIMESTAMPTZ  NOT NULL,
    location    VARCHAR(400) NOT NULL,
    total_seats INTEGER      NOT NULL CHECK (total_seats > 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
