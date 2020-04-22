# --- !Ups

DROP MATERIALIZED VIEW promoted_versions;
DROP VIEW apiv1_projects;

CREATE TYPE ASSET_TYPE AS ENUM ('content', 'uploaded_archive', 'source', 'misc');

ALTER TABLE projects
    RENAME plugin_id TO apiv1_identifier;

CREATE TABLE project_version_assets
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    project_id BIGINT REFERENCES projects ON DELETE CASCADE,
    version_id BIGINT REFERENCES project_versions ON DELETE CASCADE,
    filename   TEXT        NOT NULL,
    is_main    BOOLEAN     NOT NULL,
    asset_type ASSET_TYPE  NOT NULL,
    hash       TEXT        NOT NULL,
    filesize   BIGINT      NOT NULL,
    uses_mixin BOOLEAN     NOT NULL,
    UNIQUE (project_id, hash),
    UNIQUE (version_id, filename)
);

INSERT INTO project_version_assets (created_at, project_id, version_id, filename, is_main, asset_type, hash, filesize,
                                    uses_mixin)
SELECT created_at,
       project_id,
       id,
       file_name,
       TRUE,
       'content',
       hash,
       file_size,
       uses_mixin
FROM project_versions;

CREATE TABLE project_version_plugins
(
    id                  BIGSERIAL PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    asset_id            BIGINT REFERENCES project_version_assets ON DELETE CASCADE,
    pluginid            TEXT        NOT NULL,
    version             TEXT        NOT NULL,
    dependency_ids      TEXT[]      NOT NULL,
    dependency_versions TEXT[]      NOT NULL,
    UNIQUE (pluginid, version)
);

INSERT INTO project_version_plugins (created_at, asset_id, pluginid, version, dependency_ids, dependency_versions)
SELECT pv.created_at,
       pva.id,
       p.apiv1_identifier,
       pv.version_string,
       pv.dependency_ids,
       pv.dependency_versions
FROM project_versions pv
         JOIN project_version_assets pva ON pv.id = pva.version_id
         JOIN projects p ON pv.project_id = p.id;

ALTER TABLE project_versions
    DROP COLUMN file_size,
    DROP COLUMN hash,
    DROP COLUMN file_name,
    DROP COLUMN uses_mixin,
    DROP COLUMN dependency_ids,
    DROP COLUMN dependency_versions,
    ADD COLUMN name TEXT;

-- noinspection SqlWithoutWhere
UPDATE project_versions
SET name = version_string;

ALTER TABLE project_versions
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE project_versions
    RENAME version_string TO slug;

CREATE TABLE project_version_plugin_platforms
(
    id                      BIGSERIAL PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL,
    plugin_id               BIGINT      NOT NULL REFERENCES project_version_plugins ON DELETE CASCADE,
    platform                TEXT        NOT NULL,
    platform_version        TEXT,
    platform_coarse_version TEXT
);

INSERT INTO project_version_plugin_platforms (created_at, plugin_id, platform, platform_version,
                                              platform_coarse_version)
SELECT pvpl.created_at, pvp.id, pvpl.platform, pvpl.platform_version, pvpl.platform_coarse_version
FROM project_version_platforms pvpl
         JOIN project_version_assets pva ON pvpl.version_id = pva.version_id
         JOIN project_version_plugins pvp ON pva.id = pvp.asset_id;

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
             pv.created_at,
             pv.stability,
             pv.release_type,
             pvpp.platform,
             pvpp.platform_version,
             pvpp.platform_coarse_version,
             row_number()
             OVER (PARTITION BY pv.project_id, pvpp.platform, pvpp.platform_coarse_version ORDER BY pv.stability, pv.created_at DESC) AS row_num
      FROM project_versions pv
               JOIN project_version_assets pva ON pv.id = pva.version_id
               JOIN project_version_plugins pvp ON pva.id = pvp.asset_id
               JOIN project_version_plugin_platforms pvpp ON pvp.id = pvpp.plugin_id
      WHERE pv.visibility = 1
        AND pvpp.platform_coarse_version IS NOT NULL) sq
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

SELECT dep.dep_id, dep.dep_ver, dep_p.*
FROM project_version_assets pva
         JOIN project_version_plugins pvp ON pva.id = pvp.asset_id,
     unnest(pvp.dependency_ids, pvp.dependency_versions) AS dep(dep_id, dep_ver)
         LEFT JOIN project_version_plugins dep_pvp ON dep.dep_id = dep_pvp.pluginid
         LEFT JOIN project_version_assets dep_pva ON dep_pvp.asset_id = dep_pva.id
         LEFT JOIN projects dep_p ON dep_pva.project_id = dep_p.id
WHERE pva.version_id = :vid;

# --- !Downs

DROP MATERIALIZED VIEW promoted_versions;
DROP VIEW apiv1_projects;

CREATE TABLE project_version_plugin_platforms
(
    id                      BIGSERIAL PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL,
    plugin_id               BIGINT      NOT NULL REFERENCES project_version_plugins ON DELETE CASCADE,
    platform                TEXT        NOT NULL,
    platform_version        TEXT,
    platform_coarse_version TEXT
);

INSERT INTO project_version_platforms (created_at, version_id, platform, platform_version,
                                       platform_coarse_version)
SELECT pvpp.created_at, pva.version_id, pvpp.platform, pvpp.platform_version, pvpp.platform_coarse_version
FROM project_version_plugin_platforms pvpp
         JOIN project_version_plugins pvp ON pvpp.plugin_id = pvp.id
         JOIN project_version_assets pva ON pvp.asset_id = pva.id;

DROP TABLE project_version_plugin_platforms;

ALTER TABLE project_versions
    RENAME slug TO version_string;

ALTER TABLE project_versions
    DROP COLUMN name,
    ADD COLUMN file_size           BIGINT NOT NULL DEFAULT 1 CHECK ( file_size > 0 ),
    ADD COLUMN hash                VARCHAR(32),
    ADD COLUMN file_name           VARCHAR(255),
    ADD COLUMN uses_mixin          BOOLEAN,
    ADD COLUMN dependency_ids      TEXT[],
    ADD COLUMN dependency_versions TEXT[];

-- noinspection SqlWithoutWhere
UPDATE project_versions pv
SET file_size           = (SELECT pva.filesize
                           FROM project_version_assets pva
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    hash                = (SELECT pva.hash
                           FROM project_version_assets pva
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    file_name           = (SELECT pva.filename
                           FROM project_version_assets pva
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    uses_mixin          = (SELECT pva.uses_mixin
                           FROM project_version_assets pva
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    dependency_ids      = (SELECT pvp.dependency_ids
                           FROM project_version_assets pva
                                    JOIN project_version_plugins pvp ON pva.id = pvp.asset_id
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1),
    dependency_versions = (SELECT pvp.dependency_versions
                           FROM project_version_assets pva
                                    JOIN project_version_plugins pvp ON pva.id = pvp.asset_id
                           WHERE pva.version_id = pv.id
                           ORDER BY pva.created_at
                           LIMIT 1);

ALTER TABLE project_versions
    ALTER COLUMN file_name SET NOT NULL,
    ALTER COLUMN hash SET NOT NULL,
    ALTER COLUMN uses_mixin SET NOT NULL,
    ALTER COLUMN dependency_ids SET NOT NULL,
    ALTER COLUMN dependency_versions SET NOT NULL;

DROP TABLE project_version_plugins;
DROP TABLE project_version_assets;

ALTER TABLE projects
    RENAME apiv1_identifier TO plugin_id;

DROP TYPE ASSET_TYPE;

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
