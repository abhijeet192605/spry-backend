-- liquibase formatted sql
-- changeset library:V004

CREATE TABLE notification_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    book_id         UUID        NOT NULL,
    kafka_event_id  VARCHAR(100) NOT NULL,
    message         TEXT        NOT NULL,
    notified_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Used by NotificationConsumer to detect already-processed messages
CREATE INDEX idx_notification_log_kafka_event_id ON notification_log (kafka_event_id);

-- Supports the 90-day retention purge job
CREATE INDEX idx_notification_log_notified_at ON notification_log (notified_at);
