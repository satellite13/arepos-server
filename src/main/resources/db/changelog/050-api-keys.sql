CREATE TABLE IF NOT EXISTS public.api_keys
(
    id           UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    owner        UUID         NOT NULL,
    name         VARCHAR(200) NOT NULL,
    token_prefix VARCHAR(32)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    scopes       JSONB        NOT NULL,
    model_ids    JSONB        NULL,
    expires_at   TIMESTAMP    NULL,
    revoked_at   TIMESTAMP    NULL,
    last_used_at TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL             DEFAULT now(),
    updated_at   TIMESTAMP    NULL,
    CONSTRAINT api_keys_owner_fk
        FOREIGN KEY (owner) REFERENCES public.users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS api_keys_owner_idx ON public.api_keys (owner);
CREATE INDEX IF NOT EXISTS api_keys_token_hash_idx ON public.api_keys (token_hash);
