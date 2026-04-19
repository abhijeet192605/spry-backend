--liquibase formatted sql
--changeset spry:V002

CREATE TABLE orders (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL REFERENCES customers (id),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_value  NUMERIC(12, 2),
    order_date   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    deleted      BOOLEAN      NOT NULL DEFAULT false,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status_pending ON orders (status) WHERE status = 'PENDING';
