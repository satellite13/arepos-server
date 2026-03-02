-- Ссылка на нотацию-источник: эта версия создана копированием из другой версии (дерево версий)
ALTER TABLE public.notations
    ADD COLUMN IF NOT EXISTS source_id uuid NULL
    REFERENCES public.notations (id) ON DELETE SET NULL;

COMMENT ON COLUMN public.notations.source_id IS 'Нотация, из которой создана эта версия (копированием). NULL для созданных с нуля.';

CREATE INDEX IF NOT EXISTS notations_source_id_idx ON public.notations (source_id) WHERE source_id IS NOT NULL;
