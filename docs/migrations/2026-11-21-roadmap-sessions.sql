-- Roadmap sessions migration
-- Apply this once to the PostgreSQL database before deploying the backend.
-- The application uses ddl-auto=update, so Hibernate can create the new
-- tables/columns, but this script still backfills existing roadmaps into
-- sessions and is the explicit path for production deployments that switch to
-- versioned migrations.
--
-- Tested against PostgreSQL 15+ (Neon compatible).

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Roadmap sessions
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai.roadmap_sessions (
    id                 uuid PRIMARY KEY,
    user_id            uuid NOT NULL,
    student_profile_id uuid,
    title              varchar(255) NOT NULL,
    target_role        varchar(255),
    description        text,
    status             varchar(255) NOT NULL DEFAULT 'ACTIVE',
    last_viewed_at     timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_by         uuid,
    deleted_at         timestamptz
);

CREATE INDEX IF NOT EXISTS idx_roadmap_sessions_user_last_viewed
    ON ai.roadmap_sessions (user_id, last_viewed_at DESC);

CREATE INDEX IF NOT EXISTS idx_roadmap_sessions_user_status
    ON ai.roadmap_sessions (user_id, status);

CREATE INDEX IF NOT EXISTS idx_roadmap_sessions_user_created
    ON ai.roadmap_sessions (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 2. Link roadmaps to their session
-- ---------------------------------------------------------------------------
ALTER TABLE ai.roadmaps
    ADD COLUMN IF NOT EXISTS session_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_roadmaps_session'
    ) THEN
        ALTER TABLE ai.roadmaps
            ADD CONSTRAINT fk_roadmaps_session
            FOREIGN KEY (session_id)
            REFERENCES ai.roadmap_sessions (id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_roadmaps_session
    ON ai.roadmaps (session_id);

-- ---------------------------------------------------------------------------
-- 3. Backfill one session for every existing roadmap so old data remains
--    visible in the new session-based UI.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE _legacy_roadmap_sessions AS
SELECT
    gen_random_uuid() AS new_session_id,
    r.id              AS roadmap_id,
    r.user_id,
    r.student_profile_id,
    r.title,
    r.description,
    r.status,
    r.created_at,
    r.updated_at
FROM ai.roadmaps r
WHERE r.session_id IS NULL
  AND r.deleted_at IS NULL;

INSERT INTO ai.roadmap_sessions (
    id,
    user_id,
    student_profile_id,
    title,
    target_role,
    description,
    status,
    last_viewed_at,
    created_at,
    updated_at
)
SELECT
    new_session_id,
    user_id,
    student_profile_id,
    title,
    NULL,
    description,
    CASE status
        WHEN 'ACTIVE' THEN 'ACTIVE'
        WHEN 'COMPLETED' THEN 'COMPLETED'
        WHEN 'FAILED' THEN 'FAILED'
        ELSE 'ARCHIVED'
    END,
    updated_at,
    created_at,
    updated_at
FROM _legacy_roadmap_sessions;

UPDATE ai.roadmaps r
SET session_id = legacy.new_session_id
FROM _legacy_roadmap_sessions legacy
WHERE r.id = legacy.roadmap_id;

DROP TABLE _legacy_roadmap_sessions;

-- ---------------------------------------------------------------------------
-- 4. Link tasks to the session that produced them
-- ---------------------------------------------------------------------------
ALTER TABLE todo.todos
    ADD COLUMN IF NOT EXISTS roadmap_session_id uuid;

CREATE INDEX IF NOT EXISTS idx_todo_user_session
    ON todo.todos (user_id, roadmap_session_id);

-- Backfill tasks through their checkpoint -> roadmap -> session chain.
UPDATE todo.todos t
SET roadmap_session_id = r.session_id
FROM ai.checkpoints c
JOIN ai.roadmaps r ON r.id = c.roadmap_id
WHERE t.related_entity_id = c.id
  AND t.roadmap_session_id IS NULL
  AND r.session_id IS NOT NULL;

COMMIT;
