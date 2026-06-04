CREATE TABLE IF NOT EXISTS public.refresh_tokens
(
    id         UUID        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL             DEFAULT now(),
    used_at    TIMESTAMP   NULL,
    revoked_at TIMESTAMP   NULL,
    CONSTRAINT refresh_tokens_user_id_fk
        FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON public.refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS refresh_tokens_expires_at_idx ON public.refresh_tokens (expires_at);
