-- Project management: optimistic locking, capacity-safe uniqueness, and
-- per-role request indexes.
--
-- Apply once to PostgreSQL (Neon compatible). Hibernate also creates the
-- version columns because ddl-auto=update, but this script is the explicit
-- production path. It deduplicates existing rows before creating the partial
-- unique indexes, so it is safe to run against a populated database.
--
-- Tested against PostgreSQL 15+.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Optimistic locking columns
-- ---------------------------------------------------------------------------
ALTER TABLE project.projects ADD COLUMN IF NOT EXISTS version bigint;
UPDATE project.projects SET version = 0 WHERE version IS NULL;
ALTER TABLE project.projects ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE project.projects ALTER COLUMN version SET NOT NULL;

ALTER TABLE project.project_roles ADD COLUMN IF NOT EXISTS version bigint;
UPDATE project.project_roles SET version = 0 WHERE version IS NULL;
ALTER TABLE project.project_roles ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE project.project_roles ALTER COLUMN version SET NOT NULL;

ALTER TABLE project.team_members ADD COLUMN IF NOT EXISTS version bigint;
UPDATE project.team_members SET version = 0 WHERE version IS NULL;
ALTER TABLE project.team_members ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE project.team_members ALTER COLUMN version SET NOT NULL;

ALTER TABLE matching.applications ADD COLUMN IF NOT EXISTS version bigint;
UPDATE matching.applications SET version = 0 WHERE version IS NULL;
ALTER TABLE matching.applications ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE matching.applications ALTER COLUMN version SET NOT NULL;

ALTER TABLE matching.invitations ADD COLUMN IF NOT EXISTS version bigint;
UPDATE matching.invitations SET version = 0 WHERE version IS NULL;
ALTER TABLE matching.invitations ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE matching.invitations ALTER COLUMN version SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. Deduplicate active memberships
-- ---------------------------------------------------------------------------
-- Keep the earliest ACTIVE row per (project_id, user_id); mark the rest as
-- REMOVED so the partial unique index can be created.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY project_id, user_id
               ORDER BY joined_at, id
           ) AS rn
    FROM project.team_members
    WHERE status = 'ACTIVE' AND deleted_at IS NULL
)
UPDATE project.team_members tm
SET status = 'REMOVED',
    left_at = COALESCE(tm.left_at, now()),
    updated_at = now()
FROM ranked r
WHERE tm.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_team_members_active_project_user
    ON project.team_members (project_id, user_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Deduplicate pending applications
-- ---------------------------------------------------------------------------
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY project_id, applicant_user_id
               ORDER BY created_at, id
           ) AS rn
    FROM matching.applications
    WHERE status = 'PENDING' AND deleted_at IS NULL
)
UPDATE matching.applications a
SET status = 'WITHDRAWN',
    updated_at = now()
FROM ranked r
WHERE a.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_applications_pending_project_applicant
    ON matching.applications (project_id, applicant_user_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Deduplicate pending invitations
-- ---------------------------------------------------------------------------
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY project_id, invited_user_id
               ORDER BY created_at, id
           ) AS rn
    FROM matching.invitations
    WHERE status = 'PENDING' AND deleted_at IS NULL
)
UPDATE matching.invitations i
SET status = 'REVOKED',
    updated_at = now()
FROM ranked r
WHERE i.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invitations_pending_project_invited
    ON matching.invitations (project_id, invited_user_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 5. Management summary indexes
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_team_members_project_role_status
    ON project.team_members (project_id, project_role_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_applications_project_status_role
    ON matching.applications (project_id, status, project_role_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_invitations_project_status_role
    ON matching.invitations (project_id, status, project_role_id)
    WHERE deleted_at IS NULL;

COMMIT;
