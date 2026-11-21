-- F005: Persistent task colours.
-- Apply to prod BEFORE deploying F005 (prod uses ddl-auto: validate).
-- Idempotent: safe to run more than once.
--   psql "$DATABASE_URL" -f db/F005-task-color.sql
--
-- routine_task_template.color already exists. No data backfill: tasks created
-- before F005 keep color = NULL (specs/F005-i-want-color-to-be-persisted, FR-009).

ALTER TABLE daily_task      ADD COLUMN IF NOT EXISTS color        VARCHAR(32);
ALTER TABLE default_routine ADD COLUMN IF NOT EXISTS color_cursor INT NOT NULL DEFAULT 0;
ALTER TABLE daily_routine   ADD COLUMN IF NOT EXISTS color_cursor INT NOT NULL DEFAULT 0;
