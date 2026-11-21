-- F007: Daily reflection (plan vs reality).
-- Apply to prod BEFORE deploying F007 (prod uses ddl-auto: validate).
-- Idempotent: safe to run more than once.
--   psql "$DATABASE_URL" -f db/F007-daily-reflection.sql

CREATE TABLE IF NOT EXISTS daily_reflection (
  id                  UUID PRIMARY KEY,
  user_id             UUID NOT NULL REFERENCES users(id),
  reflection_date     DATE NOT NULL,
  color_cursor        INTEGER NOT NULL DEFAULT 0,
  feedback_text       TEXT,
  feedback_at         TIMESTAMP WITH TIME ZONE,
  content_updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ux_daily_reflection_user_date UNIQUE (user_id, reflection_date)
);

-- planned_task_id is a plain column (no FK): deleting a planned task must never
-- touch the reflection; a dangling link simply counts as "unplanned".
CREATE TABLE IF NOT EXISTS reflection_task (
  id                   UUID PRIMARY KEY,
  daily_reflection_id  UUID NOT NULL REFERENCES daily_reflection(id),
  planned_task_id      UUID,
  name                 VARCHAR(255) NOT NULL,
  description          VARCHAR(255),
  start_time           TIME NOT NULL,
  duration_minutes     INTEGER NOT NULL,
  color                VARCHAR(32)
);

-- One reflection chat per user per day (chat_conversation.type is VARCHAR(32), no column change).
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_conv_reflection
  ON chat_conversation (user_id, routine_date) WHERE type = 'DAILY_REFLECTION';
