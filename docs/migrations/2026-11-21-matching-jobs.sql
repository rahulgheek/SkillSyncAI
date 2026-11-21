-- Matching jobs + shortlist indexes
-- Apply once to PostgreSQL (Neon compatible). Hibernate also creates these
-- objects because ddl-auto=update, but this script is the explicit production
-- path and keeps existing deployments reproducible.
--
-- Tested against PostgreSQL 15+.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Background matching jobs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS matching.recommendation_jobs (
    id                 uuid PRIMARY KEY,
    type               varchar(32) NOT NULL,
    subject_id         uuid NOT NULL,
    project_id         uuid,
    requested_by       uuid NOT NULL,
    status             varchar(16) NOT NULL,
    input_hash         varchar(64),
    lock_key           varchar(80),
    candidate_count    integer NOT NULL DEFAULT 0,
    ai_candidate_count integer NOT NULL DEFAULT 0,
    ai_applied         boolean NOT NULL DEFAULT false,
    error              text,
    requested_at       timestamptz NOT NULL DEFAULT now(),
    started_at         timestamptz,
    finished_at        timestamptz,
    version            bigint
);

-- PostgreSQL allows multiple NULLs in a unique index, so finished jobs keep
-- their history while at most one active job exists per project/student.
CREATE UNIQUE INDEX IF NOT EXISTS uk_recommendation_jobs_lock_key
    ON matching.recommendation_jobs (lock_key);

CREATE INDEX IF NOT EXISTS idx_recommendation_jobs_subject
    ON matching.recommendation_jobs (subject_id, requested_at);

CREATE INDEX IF NOT EXISTS idx_recommendation_jobs_project
    ON matching.recommendation_jobs (project_id, requested_at);

CREATE INDEX IF NOT EXISTS idx_recommendation_jobs_status
    ON matching.recommendation_jobs (status, requested_at);

-- ---------------------------------------------------------------------------
-- 2. Deterministic score on recommendations (LLM fallback + blended ranking)
-- ---------------------------------------------------------------------------
ALTER TABLE matching.project_recommendations
    ADD COLUMN IF NOT EXISTS deterministic_score numeric(5, 2);

-- ---------------------------------------------------------------------------
-- 3. Shortlist indexes
-- ---------------------------------------------------------------------------
-- Hibernate may already have created these indexes from the entity
-- annotations, without the partial predicate. Recreate them so the shortlist
-- query only indexes live rows.
DROP INDEX IF EXISTS auth.idx_users_college_role;
CREATE INDEX IF NOT EXISTS idx_users_college_role
    ON auth.users (college_id, role)
    WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS profile.idx_student_skills_skill_profile;
CREATE INDEX IF NOT EXISTS idx_student_skills_skill_profile
    ON profile.student_skills (skill_id, student_profile_id)
    WHERE deleted_at IS NULL;

COMMIT;
