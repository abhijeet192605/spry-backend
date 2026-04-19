--liquibase formatted sql
--changeset spry:V003

CREATE TABLE order_items (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_name VARCHAR(300) NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    quantity     INTEGER      NOT NULL,
    line_total   NUMERIC(12, 2) NOT NULL,
    CONSTRAINT chk_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_quantity_positive       CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
