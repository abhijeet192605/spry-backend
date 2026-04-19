--liquibase formatted sql
--changeset spry:V004

CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_pending ON outbox_events (status) WHERE status = 'PENDING';
