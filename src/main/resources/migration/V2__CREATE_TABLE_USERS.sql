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

INSERT INTO users (email, password, role, first_name, last_name, phone_number, is_confirmed)
VALUES ('admin@office.local','$2a$12$yuuJTYBCwC0gzOMt.RtlLetu5rO6.kZUjDKr2zuLwsBm7CeIuUlPW',0,'Admin','Power','+40712345678', true)