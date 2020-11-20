# --- !Ups

DROP MATERIALIZED VIEW promoted_versions;
DROP VIEW apiv1_projects;

ALTER TABLE projects
    RENAME plugin_id TO apiv1_identifier;

CREATE TABLE project_assets
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    project_id BIGINT      NOT NULL REFERENCES projects ON DELETE CASCADE,
    filename   TEXT        NOT NULL,
    hash       TEXT        NOT NULL,
    filesize   BIGINT      NOT NULL,
    UNIQUE (project_id, hash)
);

ALTER TABLE projects
    ADD icon_asset_id BIGSERIAL REFERENCES project_assets ON DELETE SET NULL;

ALTER TABLE project_versions
    ADD COLUMN plugin_asset_id  BIGSERIAL UNIQUE REFERENCES project_assets,
    ADD COLUMN docs_asset_id    BIGSERIAL REFERENCES project_assets,
    ADD COLUMN sources_asset_id BIGSERIAL REFERENCES project_assets;

WITH insert_assets AS (
    INSERT INTO project_assets (created_at, project_id, filename, hash, filesize)
        SELECT created_at,
               project_id,
               id,
               file_name,
               hash,
               file_size
        FROM project_versions pv RETURNING pv.id AS version_id, id AS inserted_asset_id
)
UPDATE project_versions pv
SET plugin_asset_id = insert_assets.inserted_asset_id
WHERE insert_assets.version_id = pv.id;

ALTER TABLE project_versions
    ALTER COLUMN plugin_asset_id SET NOT NULL;

CREATE TABLE project_asset_plugins
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    asset_id   BIGINT REFERENCES project_assets ON DELETE CASCADE,
    identifier TEXT        NOT NULL,
    version    TEXT        NOT NULL,
    UNIQUE (identifier, version)
);

INSERT INTO project_asset_plugins (created_at, asset_id, identifier, version)
SELECT pv.created_at,
       pva.id,
       p.apiv1_identifier,
       pv.version_string
FROM project_versions pv
         JOIN project_assets pva ON pv.plugin_asset_id = pva.id
         JOIN projects p ON pv.project_id = p.id;

CREATE TABLE project_asset_plugin_dependencies
(
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL,
    plugin_id      BIGSERIAL   NOT NULL REFERENCES project_asset_plugins ON DELETE CASCADE,
    identifier     TEXT        NOT NULL,
    version_range  TEXT,
    version_syntax TEXT
);

INSERT INTO project_asset_plugin_dependencies (created_at, plugin_id, identifier, version_range, version_syntax)
SELECT pv.created_at, pvp.id, depid.identifier, depver.version, 'maven'
FROM project_asset_plugins pvp
         JOIN project_assets pva ON pva.id = pvp.asset_id
         JOIN project_versions pv ON pv.plugin_asset_id = pva.id,
     unnest(pv.dependency_ids) AS depid(identifier),
     unnest(pv.dependency_versions) AS depver(version);

ALTER TABLE project_versions
    DROP COLUMN file_size,
    DROP COLUMN hash,
    DROP COLUMN file_name,
    DROP COLUMN dependency_ids,
    DROP COLUMN dependency_versions,
    ADD COLUMN name TEXT;

-- noinspection SqlWithoutWhere
UPDATE project_versions
SET name = version_string;

ALTER TABLE project_versions
    ALTER COLUMN name SET NOT NULL,
    DROP COLUMN version_string,
    ADD COLUMN slug TEXT NOT NULL GENERATED ALWAYS AS ( left(
            regexp_replace(translate(lower(name), ' ', '-'), '[^a-z\-._0-9]', '', 'g'), 32) ) STORED;

CREATE TABLE project_asset_plugin_platforms
(
    id                      BIGSERIAL PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL,
    plugin_id               BIGINT      NOT NULL REFERENCES project_asset_plugins ON DELETE CASCADE,
    platform                TEXT        NOT NULL,
    platform_version        TEXT,
    platform_coarse_version TEXT
);

INSERT INTO project_asset_plugin_platforms (created_at, plugin_id, platform, platform_version, platform_coarse_version)
SELECT pvpl.created_at, pvp.id, pvpl.platform, pvpl.platform_version, pvpl.platform_coarse_version
FROM project_version_platforms pvpl
         JOIN project_versions pv ON pv.id = pvpl.version_id
         JOIN project_assets pva ON pv.plugin_asset_id = pva.id
         JOIN project_asset_plugins pvp ON pva.id = pvp.asset_id;

DROP TABLE project_version_platforms;

CREATE MATERIALIZED VIEW promoted_versions AS
SELECT sq.project_id,
       sq.name,
       array_agg(sq.platform) OVER w                AS platforms,
       array_agg(sq.platform_version) OVER w        AS platform_versions,
       array_agg(sq.platform_coarse_version) OVER w AS platform_coarse_versions,
       sq.stability,
       sq.release_type
FROM (SELECT pv.project_id,
             pv.name,
             pv.slug,
             pv.created_at,
             pv.stability,
             pv.release_type,
             pvpp.platform,
             pvpp.platform_version,
             pvpp.platform_coarse_version,
             row_number()
             OVER (PARTITION BY pv.project_id, pvpp.platform, pvpp.platform_coarse_version ORDER BY pv.stability, pv.created_at DESC) AS row_num
      FROM project_versions pv
               JOIN project_assets pva ON pv.plugin_asset_id = pva.id
               JOIN project_asset_plugins pvp ON pva.id = pvp.asset_id
               JOIN project_asset_plugin_platforms pvpp ON pvp.id = pvpp.plugin_id
      WHERE pv.visibility = 1
        AND pvpp.platform_coarse_version IS NOT NULL) sq
WHERE sq.row_num = 1
    WINDOW w AS (PARTITION BY sq.project_id, sq.slug)
ORDER BY sq.platform_version DESC;

CREATE VIEW apiv1_projects AS
SELECT p.id,
       to_jsonb(
               ARRAY(SELECT DISTINCT
                   ON (promoted.version_string) jsonb_build_object(
                                                        'version_string', promoted.name,
                                                        'platforms', promoted.platforms,
                                                        'platform_versions', promoted.platform_versions,
                                                        'platform_coarse_versions', promoted.platform_coarse_versions,
                                                        'stability', promoted.stability,
                                                        'release_type', promoted.release_type)
                     FROM promoted_versions promoted
                     WHERE promoted.project_id = p.id
                     LIMIT 5)) AS promoted_versions
FROM projects p;

# --- !Downs

DROP MATERIALIZED VIEW promoted_versions;
DROP VIEW apiv1_projects;

CREATE TABLE project_version_platforms
(
    id                      BIGSERIAL PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL,
    version_id              BIGINT      NOT NULL REFERENCES project_versions ON DELETE CASCADE,
    platform                TEXT        NOT NULL,
    platform_version        TEXT,
    platform_coarse_version TEXT
);

INSERT INTO project_version_platforms (created_at, version_id, platform, platform_version, platform_coarse_version)
SELECT pvpp.created_at, pv.id, pvpp.platform, pvpp.platform_version, pvpp.platform_coarse_version
FROM project_asset_plugin_platforms pvpp
         JOIN project_asset_plugins pvp ON pvpp.plugin_id = pvp.id
         JOIN project_assets pva ON pvp.asset_id = pva.id
         JOIN project_versions pv ON pv.plugin_asset_id = pva.id;

DROP TABLE project_asset_plugin_platforms;

ALTER TABLE project_versions
    ADD COLUMN file_size           BIGINT NOT NULL DEFAULT 1 CHECK ( file_size > 0 ),
    ADD COLUMN hash                VARCHAR(32),
    ADD COLUMN file_name           VARCHAR(255),
    ADD COLUMN dependency_ids      TEXT[],
    ADD COLUMN dependency_versions TEXT[];

-- noinspection SqlWithoutWhere
UPDATE project_versions pv
SET name                = slug,
    file_size           = (SELECT pva.filesize
                           FROM project_assets pva
                           WHERE pv.plugin_asset_id = pva.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    hash                = (SELECT pva.hash
                           FROM project_assets pva
                           WHERE pv.plugin_asset_id = pva.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    file_name           = (SELECT pva.filename
                           FROM project_assets pva
                           WHERE pv.plugin_asset_id = pva.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    dependency_ids      = (SELECT array_agg(apd.identifier)
                           FROM project_asset_plugin_dependencies apd
                                    JOIN project_asset_plugins ap ON ap.id = apd.plugin_id
                           WHERE ap.asset_id = pv.plugin_asset_id),
    dependency_versions = (SELECT array_agg(apd.version_range)
                           FROM project_asset_plugin_dependencies apd
                                    JOIN project_asset_plugins ap ON ap.id = apd.plugin_id
                           WHERE ap.asset_id = pv.plugin_asset_id);

ALTER TABLE project_versions
    RENAME name TO version_string;

ALTER TABLE project_versions
    DROP COLUMN slug,
    ALTER COLUMN file_name SET NOT NULL,
    ALTER COLUMN hash SET NOT NULL,
    ALTER COLUMN dependency_ids SET NOT NULL,
    ALTER COLUMN dependency_versions SET NOT NULL;

ALTER TABLE projects
    DROP COLUMN icon_asset_id;

DROP TABLE project_asset_plugins;
DROP TABLE project_assets;

ALTER TABLE projects
    RENAME apiv1_identifier TO plugin_id;

CREATE MATERIALIZED VIEW promoted_versions AS
SELECT sq.project_id,
       sq.name,
       array_agg(sq.platform) OVER w                AS platforms,
       array_agg(sq.platform_version) OVER w        AS platform_versions,
       array_agg(sq.platform_coarse_version) OVER w AS platform_coarse_versions,
       sq.stability,
       sq.release_type
FROM (SELECT pv.project_id,
             pv.name,
             pv.created_at,
             pv.stability,
             pv.release_type,
             pvp.platform,
             pvp.platform_version,
             pvp.platform_coarse_version,
             row_number()
             OVER (PARTITION BY pv.project_id, pvp.platform, pvp.platform_coarse_version ORDER BY pv.stability, pv.created_at DESC) AS row_num
      FROM project_versions pv
               JOIN project_version_platforms pvp ON pv.id = pvp.version_id
      WHERE pv.visibility = 1
        AND pvp.platform_coarse_version IS NOT NULL) sq
WHERE sq.row_num = 1
    WINDOW w AS (PARTITION BY sq.project_id, sq.name)
ORDER BY sq.platform_version DESC;

CREATE VIEW apiv1_projects AS
SELECT p.id,
       to_jsonb(
               ARRAY(SELECT DISTINCT
                   ON (promoted.version_string) jsonb_build_object(
                                                        'version_string', promoted.name,
                                                        'platforms', promoted.platforms,
                                                        'platform_versions', promoted.platform_versions,
                                                        'platform_coarse_versions', promoted.platform_coarse_versions,
                                                        'stability', promoted.stability,
                                                        'release_type', promoted.release_type)
                     FROM promoted_versions promoted
                     WHERE promoted.project_id = p.id
                     LIMIT 5)) AS promoted_versions
FROM projects p;
