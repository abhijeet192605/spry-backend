--liquibase formatted sql
--changeset spry:V001

CREATE TABLE customers (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    address     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_customers_email ON customers (email);
