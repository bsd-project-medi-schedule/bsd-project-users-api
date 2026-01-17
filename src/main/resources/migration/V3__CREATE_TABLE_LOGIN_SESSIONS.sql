CREATE TABLE IF NOT EXISTS login_sessions
(
    id         UUID PRIMARY KEY REFERENCES users (id),
    token      TEXT UNIQUE              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);