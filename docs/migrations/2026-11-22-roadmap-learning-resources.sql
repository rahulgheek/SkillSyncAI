-- AI roadmap learning materials.
--
-- Checkpoints and their matching tasks now store an AI-curated list of
-- learning resources (popular YouTube videos plus optional docs). The list is
-- stored as JSONB so it can evolve without another table join.
--
-- Apply once to PostgreSQL (Neon compatible). Hibernate also creates these
-- columns because ddl-auto=update, but this script is the explicit production
-- path and is safe to re-run.

BEGIN;

ALTER TABLE ai.checkpoints
    ADD COLUMN IF NOT EXISTS learning_resources jsonb;

ALTER TABLE todo.todos
    ADD COLUMN IF NOT EXISTS learning_resources jsonb;

COMMIT;
