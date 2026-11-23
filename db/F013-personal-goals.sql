-- F013: Personal goals (up to 3 per user) shown on the Personal Target page and given to Buddy.
-- Apply to prod BEFORE deploying F013 (prod uses ddl-auto: validate).
-- Idempotent: safe to run more than once.
--   psql "$DATABASE_URL" -f db/F013-personal-goals.sql
--
-- The user is referenced by id only (no JPA relation), like user_setting. The limit of
-- 3 goals per user is enforced by the service under a user-row lock
-- (specs/F013-personal-goals, data-model §1, research R2).

CREATE TABLE IF NOT EXISTS personal_goal (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_text   VARCHAR(150)             NOT NULL,
    how_to_help VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_personal_goal_user ON personal_goal (user_id, created_at);
