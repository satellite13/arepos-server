-- Ссылка на модель-источник: эта версия создана копированием из другой версии (дерево версий)
ALTER TABLE public.models
    ADD COLUMN IF NOT EXISTS source_id uuid NULL
        REFERENCES public.models (id) ON DELETE SET NULL;

COMMENT ON COLUMN public.models.source_id IS 'Модель, из которой создана эта версия (копированием). NULL для созданных с нуля.';

CREATE INDEX IF NOT EXISTS models_source_id_idx ON public.models (source_id) WHERE source_id IS NOT NULL;
