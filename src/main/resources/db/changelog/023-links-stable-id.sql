-- Сквозной идентификатор связи: не меняется при копировании модели
ALTER TABLE public.links
    ADD COLUMN IF NOT EXISTS stable_id uuid NULL;

COMMENT ON COLUMN public.links.stable_id IS 'Сквозной id связи: сохраняется при копировании модели';

UPDATE public.links
SET stable_id = gen_random_uuid()
WHERE stable_id IS NULL;

ALTER TABLE public.links
    ALTER COLUMN stable_id SET NOT NULL;

ALTER TABLE public.links
    ADD CONSTRAINT links_stable_id_key UNIQUE (stable_id);
