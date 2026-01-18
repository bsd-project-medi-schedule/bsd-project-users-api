CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email            TEXT UNIQUE,
    password         TEXT,
    role             INT,
    first_name       TEXT                     NOT NULL,
    last_name        TEXT                     NOT NULL,
    phone_number     TEXT,
    is_confirmed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);