-- liquibase formatted sql
-- changeset library:V003

CREATE TABLE wishlist (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    book_id    UUID        NOT NULL REFERENCES books (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wishlist_user_book UNIQUE (user_id, book_id)
);

-- NotificationConsumer queries this heavily when a book becomes available
CREATE INDEX idx_wishlist_book_id ON wishlist (book_id);
