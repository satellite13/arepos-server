-- Сквозной идентификатор узла: не меняется при копировании модели, нужен для сопоставления узлов между версиями при сравнении
ALTER TABLE public.nodes
    ADD COLUMN IF NOT EXISTS stable_id uuid NULL;

COMMENT ON COLUMN public.nodes.stable_id IS 'Сквозной id узла: сохраняется при копировании модели, один и тот же узел в разных версиях имеет один stable_id';

UPDATE public.nodes
SET stable_id = gen_random_uuid()
WHERE stable_id IS NULL;

ALTER TABLE public.nodes
    ALTER COLUMN stable_id SET NOT NULL;

ALTER TABLE public.nodes
    ADD CONSTRAINT nodes_stable_id_key UNIQUE (stable_id);
