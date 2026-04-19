-- liquibase formatted sql
-- changeset library:V002

CREATE TABLE books (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(300) NOT NULL,
    author              VARCHAR(200) NOT NULL,
    isbn                VARCHAR(20)  NOT NULL,
    published_year      SMALLINT     NOT NULL,
    availability_status VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    deleted             BOOLEAN      NOT NULL DEFAULT false,
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_books_published_year
        CHECK (published_year BETWEEN 1000 AND EXTRACT(YEAR FROM now())::INT + 1)
);

CREATE UNIQUE INDEX idx_books_isbn           ON books (isbn);
CREATE        INDEX idx_books_author         ON books (author);
CREATE        INDEX idx_books_status         ON books (availability_status);
CREATE        INDEX idx_books_active         ON books (id) WHERE deleted = false;
