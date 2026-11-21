-- F006: Per-user UI settings (theme, board zoom, future preferences).
-- Apply to prod BEFORE deploying F006 (prod uses ddl-auto: validate).
-- Idempotent: safe to run more than once.
--   psql "$DATABASE_URL" -f db/F006-user-settings.sql
--
-- One row per (user, setting key). No row = the default value, so new users get
-- no rows and adding a preference later needs no DDL
-- (specs/F006-user-profile-configuration-store, research R1, FR-016).

CREATE TABLE IF NOT EXISTS user_setting (
    user_id       UUID                     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key   VARCHAR(64)              NOT NULL,
    setting_value VARCHAR(255)             NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, setting_key)
);
