-- Transactional outbox для публикации model sync в STOMP (опционально, arepos.model-sync.outbox-enabled).
CREATE TABLE IF NOT EXISTS model_sync_outbox
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    model_id     UUID        NOT NULL REFERENCES models (id) ON DELETE CASCADE,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    last_error   TEXT        NULL
);

CREATE INDEX IF NOT EXISTS idx_model_sync_outbox_pending ON model_sync_outbox (created_at)
    WHERE published_at IS NULL;
