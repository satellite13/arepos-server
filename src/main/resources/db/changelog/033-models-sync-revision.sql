-- Монотонный счётчик мутаций модели для live sync (revision в STOMP payload).
ALTER TABLE models
    ADD COLUMN IF NOT EXISTS sync_revision BIGINT NOT NULL DEFAULT 0;
