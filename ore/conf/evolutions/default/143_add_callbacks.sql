# --- !Ups

CREATE TABLE project_callbacks
(
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMPTZ NOT NULL,
    project_id        BIGINT      NOT NULL REFERENCES projects ON DELETE CASCADE,
    public_id         UUID        NOT NULL,
    name              TEXT        NOT NULL,
    callback_url      TEXT,
    discord_formatted BOOLEAN     NOT NULL,
    events            TEXT[]      NOT NULL
);

# --- !Downs

DROP TABLE project_callbacks;

