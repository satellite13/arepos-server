-- 051-api-keys-mode-grants.sql
DELETE FROM public.api_keys;

ALTER TABLE public.api_keys DROP COLUMN IF EXISTS model_ids;

ALTER TABLE public.api_keys ADD COLUMN IF NOT EXISTS mode VARCHAR(16);
ALTER TABLE public.api_keys ADD COLUMN IF NOT EXISTS grants JSONB;

UPDATE public.api_keys SET mode = 'all' WHERE mode IS NULL;
ALTER TABLE public.api_keys ALTER COLUMN mode SET NOT NULL;
ALTER TABLE public.api_keys ALTER COLUMN mode SET DEFAULT 'all';

ALTER TABLE public.api_keys ALTER COLUMN scopes DROP NOT NULL;

ALTER TABLE public.api_keys DROP CONSTRAINT IF EXISTS api_keys_mode_check;
ALTER TABLE public.api_keys
    ADD CONSTRAINT api_keys_mode_check CHECK (mode IN ('all', 'grants'));
