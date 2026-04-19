-- liquibase formatted sql
-- changeset library:V005

CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

-- OutboxRelayService polls this index every 500ms — partial keeps it tiny
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
