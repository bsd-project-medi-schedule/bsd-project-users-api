CREATE TABLE IF NOT EXISTS login_sessions
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    label      TEXT UNIQUE              NOT NULL,
    url        TEXT UNIQUE              NOT NULL,
    icon_url   TEXT UNIQUE              NOT NULL
);