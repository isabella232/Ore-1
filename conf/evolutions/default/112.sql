# --- !Ups

CREATE TABLE messages
(
  id           BIGSERIAL PRIMARY KEY,
  created_at   TIMESTAMP NOT NULL,
  creator_id   BIGINT REFERENCES users,
  allow_edit   BOOLEAN   NOT NULL DEFAULT FALSE,
  is_localized BOOLEAN   NOT NULL,
  message      TEXT      NOT NULL,
  message_args TEXT []   NOT NULL DEFAULT ARRAY [] :: TEXT [],
  importance   INT
);

-- Logged actions (new)
ALTER TABLE logged_actions
  ADD COLUMN message_id BIGINT REFERENCES messages;

-- Notifications
ALTER TABLE notifications
  ADD COLUMN message_id BIGINT REFERENCES messages;

INSERT INTO messages (created_at, creator_id, is_localized, message, message_args)
SELECT sq.created_at, sq.creator_id, sq.is_localized, sq.message, sq.message_args
FROM (SELECT n.created_at,
             n.origin_id                                                               AS creator_id,
             array_length(n.message_args, 1) != 1                                      AS is_localized,
             n.message_args [ 1 ]                                                      AS message,
             n.message_args [ 2 : array_length(n.message_args, 1) ]                    AS message_args,
             row_number() OVER (PARTITION BY n.message_args ORDER BY n.created_at ASC) AS row
      FROM notifications n) sq
WHERE sq.row = 1;

UPDATE notifications n
SET message_id = m.id
FROM messages m
WHERE n.message_args = (m.message || m.message_args);

ALTER TABLE notifications
  ALTER COLUMN message_id SET NOT NULL,
  DROP COLUMN message_args;

-- Flags
ALTER TABLE project_flags
  ADD COLUMN message_id BIGINT REFERENCES messages;

INSERT INTO messages (created_at, creator_id, is_localized, message)
SELECT sq.created_at, sq.creator_id, sq.is_localized, sq.message
FROM (SELECT f.created_at,
             f.user_id                                                            AS creator_id,
             FALSE                                                                AS is_localized,
             f.comment                                                            AS message,
             row_number() OVER (PARTITION BY f.comment ORDER BY f.created_at ASC) AS row
      FROM project_flags f) sq
WHERE sq.row = 1;

UPDATE project_flags f
SET message_id = m.id
FROM messages m
WHERE f.comment = m.message;

ALTER TABLE project_flags
  ALTER COLUMN message_id SET NOT NULL,
  DROP COLUMN comment;

-- Version Visibility changes
ALTER TABLE project_version_visibility_changes
  ADD COLUMN message_id BIGINT REFERENCES messages;

INSERT INTO messages (created_at, creator_id, is_localized, message)
SELECT sq.created_at, sq.creator_id, sq.is_localized, sq.message
FROM (SELECT vc.created_at,
             vc.created_by                                                          AS creator_id,
             FALSE                                                                  AS is_localized,
             vc.comment                                                             AS message,
             row_number() OVER (PARTITION BY vc.comment ORDER BY vc.created_at ASC) AS row
      FROM project_version_visibility_changes vc) sq
WHERE sq.row = 1;

UPDATE project_version_visibility_changes vc
SET message_id = m.id
FROM messages m
WHERE vc.comment = m.message;

ALTER TABLE project_version_visibility_changes
  ALTER COLUMN message_id SET NOT NULL,
  DROP COLUMN comment;

-- Project Visibility changes
ALTER TABLE project_visibility_changes
  ADD COLUMN message_id BIGINT REFERENCES messages;

INSERT INTO messages (created_at, creator_id, is_localized, message)
SELECT sq.created_at, sq.creator_id, sq.is_localized, sq.message
FROM (SELECT vc.created_at,
             vc.created_by                                                          AS creator_id,
             FALSE                                                                  AS is_localized,
             vc.comment                                                             AS message,
             row_number() OVER (PARTITION BY vc.comment ORDER BY vc.created_at ASC) AS row
      FROM project_visibility_changes vc) sq
WHERE sq.row = 1;

UPDATE project_visibility_changes vc
SET message_id = m.id
FROM messages m
WHERE vc.comment = m.message;

ALTER TABLE project_visibility_changes
  ALTER COLUMN message_id SET NOT NULL,
  DROP COLUMN comment;

-- Reviews
CREATE TABLE project_version_reviews_messages
(
  id         BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  review_id  BIGINT    NOT NULL REFERENCES project_version_reviews,
  message_id BIGINT    NOT NULL REFERENCES messages,
  action     VARCHAR(16)
);

INSERT INTO messages (created_at, creator_id, is_localized, message)
SELECT sq3.created_at, sq3.creator_id, FALSE, sq3.message
FROM (SELECT sq2.created_at,
             sq2.creator_id,
             sq2.message,
             row_number() OVER (PARTITION BY sq2.message ORDER BY sq2.created_at ASC) AS row
      FROM (SELECT to_timestamp((sq1.comment ->> 'time') :: DOUBLE PRECISION / 1000) AS created_at,
                   sq1.creator_id,
                   sq1.comment ->> 'message'                                         AS message
            FROM (SELECT jsonb_array_elements(r.comment -> 'messages') AS comment, r.user_id AS creator_id
                  FROM project_version_reviews r) sq1) sq2) sq3
WHERE sq3.row = 1;

INSERT INTO project_version_reviews_messages (review_id, message_id, created_at, action)
SELECT sq.id AS review_id, m.id AS message_id, sq.msg_time AS message_time, sq.action
FROM (SELECT r.id,
             jsonb_array_elements(r.comment -> 'messages') ->> 'message' AS message,
             to_timestamp((jsonb_array_elements(r.comment -> 'messages') ->> 'time') :: DOUBLE PRECISION /
                          1000)                                          AS msg_time,
             jsonb_array_elements(r.comment -> 'messages') ->> 'action'  AS action
      FROM project_version_reviews r) sq
       JOIN messages m ON sq.message = m.message;

ALTER TABLE project_version_reviews
  DROP COLUMN comment;

-- Projects
CREATE TABLE project_notes_messages
(
  id         BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  project_id BIGINT    NOT NULL REFERENCES projects,
  message_id BIGINT    NOT NULL REFERENCES messages
);

INSERT INTO messages (created_at, creator_id, is_localized, message)
SELECT sq3.created_at, sq3.creator_id, FALSE, sq3.message
FROM (SELECT sq2.created_at,
             sq2.creator_id,
             sq2.message,
             row_number() OVER (PARTITION BY sq2.message ORDER BY sq2.created_at ASC) AS row
      FROM (SELECT to_timestamp((sq1.comment ->> 'time') :: DOUBLE PRECISION / 1000) AS created_at,
                   (sq1.comment ->> 'user') :: BIGINT                                AS creator_id,
                   sq1.comment ->> 'message'                                         AS message
            FROM (SELECT jsonb_array_elements(p.notes -> 'messages') AS comment FROM projects p) sq1) sq2) sq3
WHERE sq3.row = 1;

INSERT INTO project_notes_messages (project_id, message_id, created_at)
SELECT sq.id AS project_id, m.id AS message_id, sq.msg_time AS message_time
FROM (SELECT p.id,
             jsonb_array_elements(p.notes -> 'messages') ->> 'message' AS message,
             to_timestamp((jsonb_array_elements(p.notes -> 'messages') ->> 'time') :: DOUBLE PRECISION /
                          1000)                                        AS msg_time
      FROM projects p) sq
       JOIN messages m ON sq.message = m.message;

ALTER TABLE projects
  DROP COLUMN notes;

# --- !Downs

-- Projects
ALTER TABLE projects
  ADD COLUMN notes JSONB DEFAULT '{}'::JSONB NOT NULL;

UPDATE projects p
SET notes = json_build_object('messages', array_agg(
    json_build_object('message', sq.message, 'user', sq.creator_id, 'time', EXTRACT(EPOCH FROM sq.message_time) * 1000)))
FROM (SELECT pm.message_time, pm.project_id, m.creator_id, m.message
      FROM project_notes_messages pm
             JOIN messages m on pm.message_id = m.id) sq
WHERE sq.project_id = p.id;

DROP TABLE project_notes_messages;

-- Reviews
ALTER TABLE project_version_reviews
  ADD COLUMN comment JSONB DEFAULT '{}'::JSONB NOT NULL;

UPDATE project_version_reviews r
SET comment = json_build_object('messages', array_agg(json_build_object('message', sq.message, 'time', EXTRACT(EPOCH FROM sq.created_at) * 1000)))
FROM (SELECT rm.created_at, rm.review_id, m.message
      FROM project_version_reviews_messages rm
             JOIN messages m on rm.message_id = m.id) sq
WHERE sq.review_id = r.id;

DROP TABLE project_version_reviews_messages;

-- Project Visibility changes
ALTER TABLE project_visibility_changes
  ADD COLUMN comment TEXT;

UPDATE project_visibility_changes vc
SET comment = m.message
FROM messages m
WHERE vc.message_id = m.id;

ALTER TABLE project_visibility_changes
  ALTER COLUMN comment SET NOT NULL,
  DROP COLUMN message_id;

-- Version Visibility changes
ALTER TABLE project_version_visibility_changes
  ADD COLUMN comment TEXT;

ALTER TABLE project_version_visibility_changes
  ALTER COLUMN comment SET NOT NULL,
  DROP COLUMN message_id;

UPDATE project_version_visibility_changes vc
SET comment = m.message
FROM messages m
WHERE vc.message_id = m.id;

-- Flags
ALTER TABLE project_flags
  ADD COLUMN comment VARCHAR(255);

UPDATE project_flags f
SET comment = m.message
FROM messages m
WHERE f.message_id = m.id;

ALTER TABLE project_flags
  DROP COLUMN message_id,
  ALTER COLUMN comment SET NOT NULL;

-- Notifications
ALTER TABLE notifications
  ADD message_args VARCHAR(255) [];

UPDATE notifications n
SET message_args = m.message || m.message_args
FROM messages m
WHERE n.message_id = m.id;

ALTER TABLE notifications
  ALTER COLUMN message_args SET NOT NULL,
  DROP COLUMN message_id;

-- Logged actions (new)
ALTER TABLE logged_actions
  DROP COLUMN message_id;

DROP TABLE messages;