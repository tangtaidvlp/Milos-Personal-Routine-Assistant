-- F004: Persist Buddy chat history.
-- Apply to prod BEFORE deploying F004 (prod uses ddl-auto: validate).
-- Idempotent: safe to run more than once.
--   psql "$DATABASE_URL" -f db/F004-chat-history.sql

CREATE TABLE IF NOT EXISTS chat_conversation (
  id                      UUID PRIMARY KEY,
  user_id                 UUID NOT NULL REFERENCES users(id),
  type                    VARCHAR(32) NOT NULL,
  default_routine_id      UUID,
  routine_date            DATE,
  last_message_seq        BIGINT NOT NULL DEFAULT 0,
  summary_text            TEXT,
  summarized_through_seq  BIGINT NOT NULL DEFAULT 0,
  summary_updated_at      TIMESTAMP WITH TIME ZONE,
  created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_conv_default
  ON chat_conversation (user_id, default_routine_id) WHERE type = 'DEFAULT_ROUTINE';
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_conv_daily
  ON chat_conversation (user_id, routine_date) WHERE type = 'DAILY_ROUTINE';

CREATE TABLE IF NOT EXISTS chat_message (
  id               UUID PRIMARY KEY,
  conversation_id  UUID NOT NULL REFERENCES chat_conversation(id),
  seq              BIGINT NOT NULL,
  role             VARCHAR(16) NOT NULL,
  content          TEXT NOT NULL,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ux_chat_message_seq UNIQUE (conversation_id, seq)
);
