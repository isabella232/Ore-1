# --- !Ups

CREATE TABLE discourse_jobs
(
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMP NOT NULL,
    project_id     BIGINT,
    version_id     BIGINT,
    topic_id       INT,
    poster         VARCHAR(255),
    job_type       INT       NOT NULL,
    retry_in       TIMESTAMP,
    attempts       INT       NOT NULL,
    last_requested TIMESTAMP NOT NULL,
    visibility     BOOLEAN   NOT NULL,
    CONSTRAINT discourse_jobs_constrain_no_duplicates UNIQUE (project_id, job_type, version_id)
);

# --- !Downs

DROP TABLE discourse_jobs;
